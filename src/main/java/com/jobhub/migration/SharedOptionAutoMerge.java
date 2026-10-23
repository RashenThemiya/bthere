package com.jobhub.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.sql.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/** Offline JDBC migration: no Spring context, no HTTP endpoint, no automatic production startup. */
public final class SharedOptionAutoMerge {
    static final String KEY = "fully_shared_options_v2_auto";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String,String> IDS = new LinkedHashMap<>();
    static {
        IDS.put("service_option","option_id"); IDS.put("service_type_option",null);
        IDS.put("service_custom_field","field_id"); IDS.put("service_custom_field_option",null);
        IDS.put("provider_service_option","provider_option_id");
        IDS.put("service_provider_type_assignment","assignment_id");
        IDS.put("provider_custom_field_submission","submission_id");
        IDS.put("job_rate","rate_id"); IDS.put("job_rate_condition",null);
        IDS.put("service_option_map_marker_rule","marker_rule_id"); IDS.put("service_option_map_marker_condition",null);
        IDS.put("service_type_document_requirement","requirement_id");
        IDS.put("customer_eligibility_rule","rule_id");
        IDS.put("service_provider_document","document_id");
    }
    private final Connection db;
    private final Map<Long,Long> fieldAliases;
    private Map<Long,Map<String,Object>> options, fields, assignments;
    private Map<Long,Map<String,Object>> mergedFields;
    private final Map<Long,Set<Long>> fieldOptions = new HashMap<>();
    private final Map<Long,Long> fieldMap = new LinkedHashMap<>();
    private final Set<String> pending = new HashSet<>();

    public SharedOptionAutoMerge(Connection db) { this(db, Map.of()); }

    /** Explicit equivalences for legacy duplicates whose codes differ; normal winner policy still applies. */
    public SharedOptionAutoMerge(Connection db, Map<Long,Long> fieldAliases) {
        this.db=db;
        this.fieldAliases=Map.copyOf(fieldAliases);
    }

    public static void main(String[] args) throws Exception {
        if (args.length!=1 || !"--apply".equals(args[0]))
            throw new IllegalArgumentException("Use --apply with DB_URL, DB_USERNAME and DB_PASSWORD for an offline migration");
        try (var db=DriverManager.getConnection(required("DB_URL"),required("DB_USERNAME"),required("DB_PASSWORD"))) {
            System.out.println(new SharedOptionAutoMerge(db, parseAliases(System.getenv("SHARED_OPTION_FIELD_ALIASES"))).apply());
        }
    }
    static Map<Long,Long> parseAliases(String value) {
        var aliases=new LinkedHashMap<Long,Long>();
        if(value==null || value.isBlank()) return aliases;
        for(String pair:value.split(",",-1)) {
            String[] ids=pair.trim().split(":",-1);
            if(ids.length!=2) throw new IllegalArgumentException("Field aliases must use fieldId:equivalentFieldId pairs");
            long a=Long.parseLong(ids[0].trim()), b=Long.parseLong(ids[1].trim());
            if(a<=0 || b<=0 || a==b || aliases.putIfAbsent(a,b)!=null)
                throw new IllegalArgumentException("Invalid or repeated field alias");
        }
        return aliases;
    }
    private static String required(String key) {
        var value=System.getenv(key);
        if(value==null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable "+key);
        return value;
    }

    public Map<String,Integer> apply() throws Exception {
        if(!db.getAutoCommit()) throw new IllegalStateException("Use a dedicated auto-commit maintenance connection");
        execute("CREATE TABLE IF NOT EXISTS shared_option_merge_archive (migration_key VARCHAR(96) NOT NULL, table_name VARCHAR(96) NOT NULL, row_key VARCHAR(64) NOT NULL, payload_json LONGTEXT NOT NULL, PRIMARY KEY(migration_key,table_name,row_key))");
        execute("CREATE TABLE IF NOT EXISTS shared_option_merge_state (migration_key VARCHAR(96) PRIMARY KEY, status VARCHAR(32) NOT NULL)");
        addColumn("service_option","map_marker_field_id"); addColumn("provider_service_option","provider_id");
        var state=read("shared_option_merge_state").stream().filter(r->KEY.equals(r.get("migration_key"))).findFirst();
        if(state.isPresent()) { finishSchema(); return Map.of("alreadyMerged",1); }
        db.setAutoCommit(false);
        var counts=new LinkedHashMap<String,Integer>();
        try {
            for(String table:IDS.keySet()) for(var row:read(table)) archive(table,row);
            options=index(read("service_option"),"option_id");
            fields=index(read("service_custom_field"),"field_id");
            assignments=index(read("service_provider_type_assignment"),"assignment_id");
            for(var row:read("service_custom_field_option")) fieldOptions.computeIfAbsent(id(row,"field_id"),k->new LinkedHashSet<>()).add(id(row,"option_id"));
            for(var f:fields.values()) if(!fieldOptions.containsKey(id(f,"field_id")) && f.get("option_id")!=null)
                fieldOptions.put(id(f,"field_id"),new LinkedHashSet<>(Set.of(id(f,"option_id"))));
            counts.put("mergedFields",mergeFields());
            mergedFields=index(read("service_custom_field"),"field_id");
            counts.put("mergedAnswers",mergeAnswers());
            counts.put("mergedRegistrations",mergeRegistrations());
            remapConditions("job_rate","rate_id","job_rate_condition","service_option_id");
            remapConditions("service_option_map_marker_rule","marker_rule_id","service_option_map_marker_condition","option_id");
            counts.put("retiredPrices",mergeRules("job_rate","rate_id","job_rate_condition","service_option_id",true));
            counts.put("retiredMarkers",mergeRules("service_option_map_marker_rule","marker_rule_id","service_option_map_marker_condition","option_id",false));
            mergeSelectors(); mergeDocuments(); mergeEligibility(); recheckMergedEvidence();
            insert("shared_option_merge_state",Map.of("migration_key",KEY,"status","DATA_MERGED"));
            db.commit();
        } catch(Exception e) { db.rollback(); throw e; }
        finally { db.setAutoCommit(true); }
        finishSchema();
        return counts;
    }

    private int mergeFields() throws Exception {
        var scoped=fields.values().stream().filter(f->!fieldOptions.getOrDefault(id(f,"field_id"),Set.of()).isEmpty()).toList();
        var parent=new HashMap<Long,Long>(); scoped.forEach(f->parent.put(id(f,"field_id"),id(f,"field_id")));
        for(var alias:fieldAliases.entrySet()) {
            var a=fields.get(alias.getKey()); var b=fields.get(alias.getValue());
            if(!parent.containsKey(alias.getKey()) || !parent.containsKey(alias.getValue())
                    || !Objects.equals(a.get("scope"),b.get("scope"))
                    || Collections.disjoint(fieldOptions.get(alias.getKey()),fieldOptions.get(alias.getValue())))
                throw new IllegalArgumentException("Field aliases must reference existing fields of the same scope sharing an option");
            parent.put(root(parent,alias.getKey()),root(parent,alias.getValue()));
        }
        for(int i=0;i<scoped.size();i++) for(int j=0;j<i;j++) {
            var a=scoped.get(i);var b=scoped.get(j);
            if(Objects.equals(a.get("scope"),b.get("scope")) && Objects.equals(a.get("code"),b.get("code"))
                    && !Collections.disjoint(fieldOptions.get(id(a,"field_id")),fieldOptions.get(id(b,"field_id"))))
                parent.put(root(parent,id(a,"field_id")),root(parent,id(b,"field_id")));
        }
        var groups=new LinkedHashMap<Long,List<Map<String,Object>>>();
        for(var f:scoped) groups.computeIfAbsent(root(parent,id(f,"field_id")),k->new ArrayList<>()).add(f);
        int retired=0;
        for(var group:groups.values()) {
            var opts=new LinkedHashSet<Long>();group.forEach(f->opts.addAll(fieldOptions.get(id(f,"field_id"))));
            group.sort(Comparator.<Map<String,Object>>comparingInt(f->"ACTIVE".equals(f.get("status"))?0:1)
                    .thenComparingInt(f->opts.stream().anyMatch(o->Objects.equals(owner(o),f.get("service_provider_type_id")))?0:1)
                    .thenComparing(f->stamp(f,"updated_at"),Comparator.reverseOrder()).thenComparingLong(f->id(f,"field_id")));
            var winner=new LinkedHashMap<>(group.get(0));long target=id(winner,"field_id");
            var compatible=group.stream().filter(f->Objects.equals(f.get("field_type"),winner.get("field_type")))
                    .filter(f->!"ACTIVE".equals(winner.get("status")) || "ACTIVE".equals(f.get("status"))).toList();
            if(group.size()>1) {
                winner.put("required",compatible.stream().anyMatch(f->truth(f.get("required"))));
                winner.put("requires_approval",compatible.stream().anyMatch(f->truth(f.get("requires_approval"))));
                if(Set.of("SELECT","MULTI_SELECT").contains(text(winner,"field_type"))) winner.put("options_json",mergeChoices(compatible));
                winner.put("version",number(winner.get("version"),1)+1);
            }
            update("service_custom_field","field_id",winner);
            for(var f:group) {
                long old=id(f,"field_id");fieldMap.put(old,target);
                execute("DELETE FROM service_custom_field_option WHERE field_id=?",old);
                if(old!=target) {
                    String code=text(f,"code");
                    execute("UPDATE service_custom_field SET status='INACTIVE', option_id=NULL, code=? WHERE field_id=?",
                            code.substring(0,Math.min(60,code.length()))+"_MERGED_"+old,old);retired++;
                }
            }
            for(Long option:opts) insert("service_custom_field_option",Map.of("field_id",target,"option_id",option));
            fieldOptions.put(target,opts);
        }
        return retired;
    }

    private String mergeChoices(List<Map<String,Object>> rows) throws Exception {
        var choices=new LinkedHashMap<String,JsonNode>();
        for(var row:rows) for(var node:json(row.get("options_json"))) {
            String value=node.isTextual()?node.asText():node.path("value").asText();
            if(value.isBlank()) continue;
            var old=choices.get(value);
            if(old==null) choices.put(value,node.deepCopy());
            else if(old.isTextual() && node.isObject()) choices.put(value,node.deepCopy());
            else if(old.isObject() && node.isObject()) node.properties().forEach(e->{
                if(!old.hasNonNull(e.getKey())) ((ObjectNode)old).set(e.getKey(),e.getValue());
            });
        }
        return JSON.writeValueAsString(choices.values());
    }

    private int mergeAnswers() throws Exception {
        var current=index(read("service_custom_field"),"field_id");
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();int retired=0;
        for(var source:read("provider_custom_field_submission")) {
            var row=new LinkedHashMap<>(source);long old=id(row,"field_id"), target=fieldMap.getOrDefault(old,old);
            var original=fields.get(old);var merged=current.get(target);var assignment=assignments.get(id(row,"assignment_id"));
            if(original==null || merged==null || assignment==null) throw new SQLException("Orphan provider answer; restore its referenced record");
            boolean incompatible=!Objects.equals(original.get("field_type"),merged.get("field_type"))
                    || !Objects.equals(original.get("value_source"),merged.get("value_source"))
                    || "INACTIVE".equals(original.get("status")) && "ACTIVE".equals(merged.get("status"));
            if(incompatible) { if(!"INACTIVE".equals(original.get("status"))) markPending(assignment,old);
                delete("provider_custom_field_submission","submission_id",id(row,"submission_id"));retired++;continue; }
            if(truth(merged.get("requires_approval")) && !truth(original.get("requires_approval"))) {
                row.put("verification_status","PENDING");row.put("reviewed_by",null);row.put("reviewed_at",null);markPending(assignment,old);
            }
            row.put("field_id",target);row.put("provider_id",assignment.get("service_provider_id"));
            row.put("service_provider_type_id",assignment.get("service_provider_type_id"));
            row.put("reusable_code",merged.get("code"));row.put("reusable_field_type",merged.get("field_type"));
            row.put("reuse_scope",merged.getOrDefault("reuse_scope","ASSIGNMENT"));
            row.put("_was_canonical",old==target);
            groups.computeIfAbsent(id(row,"assignment_id")+":"+target,k->new ArrayList<>()).add(row);
        }
        for(var group:groups.values()) {
            group.sort(Comparator.<Map<String,Object>>comparingInt(r->truth(r.get("_was_canonical"))?0:1)
                    .thenComparing(r->stamp(r,"reviewed_at"),Comparator.reverseOrder())
                    .thenComparing(r->stamp(r,"updated_at"),Comparator.reverseOrder()).thenComparingLong(r->id(r,"submission_id")));
            var chosen=group.get(0);for(int i=1;i<group.size();i++){delete("provider_custom_field_submission","submission_id",id(group.get(i),"submission_id"));retired++;}
            chosen.remove("_was_canonical"); update("provider_custom_field_submission","submission_id",chosen);
        }
        return retired;
    }
    private void markPending(Map<String,Object> assignment,long field) {
        for(Long option:fieldOptions.getOrDefault(field,Set.of())) pending.add(assignment.get("service_provider_id")+":"+option);
    }

    private int mergeRegistrations() throws Exception {
        var groups=new LinkedHashMap<String,List<Map<String,Object>>>();int retired=0;
        for(var original:read("provider_service_option")) {
            var row=new LinkedHashMap<>(original);var assignment=assignments.get(id(row,"assignment_id"));
            if(assignment==null || assignment.get("service_provider_id")==null) throw new SQLException("Orphan provider registration");
            row.put("provider_id",assignment.get("service_provider_id"));
            groups.computeIfAbsent(row.get("provider_id")+":"+row.get("option_id"),k->new ArrayList<>()).add(row);
        }
        for(var entry:groups.entrySet()) {
            var group=entry.getValue();
            group.sort(Comparator.<Map<String,Object>>comparingInt(r->r.get("reviewed_at")!=null?0:1)
                    .thenComparing(r->stamp(r,"reviewed_at"),Comparator.reverseOrder())
                    .thenComparingInt(r->Objects.equals(owner(id(r,"option_id")),assignments.get(id(r,"assignment_id")).get("service_provider_type_id"))?0:1)
                    .thenComparing(r->stamp(r,"updated_at"),Comparator.reverseOrder()).thenComparingLong(r->id(r,"provider_option_id")));
            var chosen=group.get(0);
            for(int i=1;i<group.size();i++){delete("provider_service_option","provider_option_id",id(group.get(i),"provider_option_id"));retired++;}
            if(pending.contains(entry.getKey())) {chosen.put("verification_status","PENDING");chosen.put("reviewed_by",null);chosen.put("reviewed_at",null);}
            update("provider_service_option","provider_option_id",chosen);
            mergeApprovedCapabilities(chosen);
        }
        return retired;
    }

    private void mergeApprovedCapabilities(Map<String,Object> registration) throws Exception {
        long provider=id(registration,"provider_id"), option=id(registration,"option_id");
        var answers=read("provider_custom_field_submission");
        for(var field:mergedFields.values()) {
            long fieldId=id(field,"field_id");
            if(!"ACTIVE".equals(field.get("status")) || !"MULTI_SELECT".equals(field.get("field_type"))
                    || !fieldOptions.getOrDefault(fieldId,Set.of()).contains(option)) continue;
            var sources=answers.stream().filter(a->Objects.equals(a.get("provider_id"),provider)
                    && Objects.equals(a.get("field_id"),fieldId)).toList();
            if(sources.size()<2 || sources.stream().anyMatch(a->!"APPROVED".equals(a.get("verification_status")))) continue;
            var values=new LinkedHashSet<String>();boolean valid=true;
            for(var source:sources) {var value=json(source.get("value_json"));if(!value.isArray()){valid=false;break;}
                for(var item:value) if(!item.isTextual() || !choiceExists(field,item.asText())) valid=false;else values.add(item.asText());}
            if(!valid) continue;
            var own=sources.stream().filter(a->Objects.equals(a.get("assignment_id"),registration.get("assignment_id"))).findFirst();
            var target=new LinkedHashMap<>(own.orElse(sources.get(0)));
            String merged=JSON.writeValueAsString(values);
            if(own.isPresent() && json(own.get().get("value_json")).equals(json(merged))) continue;
            target.put("assignment_id",registration.get("assignment_id"));target.put("value_json",merged);
            target.put("reviewed_by",null);target.put("reviewed_at",null);
            String note="Merged existing APPROVED choices; archive "+KEY+"; source submissions "+sources.stream().map(a->a.get("submission_id").toString()).toList();
            target.put("review_note",note.substring(0,Math.min(1000,note.length())));
            if(own.isPresent()) update("provider_custom_field_submission","submission_id",target);
            else {target.remove("submission_id");insert("provider_custom_field_submission",target);}
        }
    }

    private void remapConditions(String table,String key,String conditionTable,String optionColumn) throws Exception {
        var parents=index(read(table),key);var groups=read(conditionTable).stream().collect(Collectors.groupingBy(r->id(r,key)));
        for(var entry:groups.entrySet()) {
            var parent=parents.get(entry.getKey());var canonical=new LinkedHashMap<Long,String>();boolean invalid=false;
            if(parent==null) throw new SQLException("Orphan condition in "+conditionTable);
            for(var c:entry.getValue()) {
                long target=fieldMap.getOrDefault(id(c,"field_id"),id(c,"field_id"));String value=text(c,"choice_value");
                String old=canonical.putIfAbsent(target,value);if(old!=null && !old.equals(value)) invalid=true;
                var field=indexedField(target);
                if(field==null || !Set.of("SELECT","MULTI_SELECT").contains(text(field,"field_type"))
                        || !"ACTIVE".equals(field.get("status")) || !truth(field.get("customer_visible")) || !truth(field.get("searchable"))
                        || !choiceExists(field,value)) invalid=true;
            }
            execute("DELETE FROM "+q(conditionTable)+" WHERE "+q(key)+"=?",entry.getKey());
            for(var c:canonical.entrySet()) insert(conditionTable,Map.of(key,entry.getKey(),"field_id",c.getKey(),"choice_value",c.getValue()));
            if(invalid) execute("UPDATE "+q(table)+" SET status='INACTIVE' WHERE "+q(key)+"=?",entry.getKey());
        }
    }
    private Map<String,Object> indexedField(long id) { return mergedFields.get(id); }
    private boolean choiceExists(Map<String,Object> field,String value) throws Exception {
        for(var c:json(field.get("options_json"))) if(value.equals(c.isTextual()?c.asText():c.path("value").asText())) return true;
        return false;
    }

    private int mergeRules(String table,String key,String conditionsTable,String optionColumn,boolean prices) throws Exception {
        var conditionRows=read(conditionsTable).stream().collect(Collectors.groupingBy(r->id(r,key)));
        var active=read(table).stream().filter(r->"ACTIVE".equals(r.get("status")) && r.get(optionColumn)!=null).collect(Collectors.toCollection(ArrayList::new));
        active.sort(Comparator.<Map<String,Object>>comparingInt(r->Objects.equals(owner(id(r,optionColumn)),r.get("service_provider_type_id"))?0:1)
                .thenComparing(r->stamp(r,"updated_at"),Comparator.reverseOrder()).thenComparingLong(r->id(r,key)));
        var kept=new ArrayList<Map<String,Object>>();int retired=0;
        for(var row:active) {
            boolean duplicate=false;
            for(var other:kept) if(Objects.equals(row.get(optionColumn),other.get(optionColumn))
                    && (!prices || Objects.equals(row.get("market_id"),other.get("market_id")))
                    && compatible(conditionRows.getOrDefault(id(row,key),List.of()),conditionRows.getOrDefault(id(other,key),List.of()))
                    && (!prices || windowsOverlap(row,other) && areasOverlap(row,other))) { duplicate=true;break; }
            if(duplicate){execute("UPDATE "+q(table)+" SET status='INACTIVE' WHERE "+q(key)+"=?",id(row,key));retired++;}
            else kept.add(row);
        }
        for(var row:read(table)) if(row.get(optionColumn)!=null) execute("UPDATE "+q(table)+" SET service_provider_type_id=? WHERE "+q(key)+"=?",owner(id(row,optionColumn)),id(row,key));
        return retired;
    }
    private boolean compatible(List<Map<String,Object>> a,List<Map<String,Object>> b) {
        if(a.size()!=b.size()) return false;
        for(var x:a) for(var y:b) if(Objects.equals(x.get("field_id"),y.get("field_id")) && !Objects.equals(x.get("choice_value"),y.get("choice_value"))) return false;
        return true;
    }
    private boolean windowsOverlap(Map<String,Object> a,Map<String,Object> b) {
        return !(a.get("effective_to")!=null && b.get("effective_from")!=null && stamp(a,"effective_to").compareTo(stamp(b,"effective_from"))<0)
                && !(b.get("effective_to")!=null && a.get("effective_from")!=null && stamp(b,"effective_to").compareTo(stamp(a,"effective_from"))<0);
    }
    private boolean areasOverlap(Map<String,Object> a,Map<String,Object> b) {
        var x=a.get("area_radius_km");var y=b.get("area_radius_km");
        if(x==null || y==null) return x==null && y==null;
        double r=decimal(x);if(Double.compare(r,decimal(y))!=0) return false;
        double lat=Math.toRadians(decimal(b.get("area_latitude"))-decimal(a.get("area_latitude")));
        double lon=Math.toRadians(decimal(b.get("area_longitude"))-decimal(a.get("area_longitude")));
        double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(decimal(a.get("area_latitude"))))*Math.cos(Math.toRadians(decimal(b.get("area_latitude"))))*Math.pow(Math.sin(lon/2),2);
        return 6371.0088*2*Math.asin(Math.sqrt(Math.min(1,h)))<=2*r;
    }

    private void mergeSelectors() throws Exception {
        var links=read("service_type_option");
        for(var option:options.values()) {
            long id=id(option,"option_id");var candidates=links.stream().filter(r->id(r,"option_id")==id).sorted(
                    Comparator.<Map<String,Object>>comparingInt(r->Objects.equals(owner(id),r.get("service_provider_type_id"))?0:1).thenComparingLong(r->id(r,"service_provider_type_id"))).toList();
            Object selector=option.get("map_marker_field_id");
            if(selector==null) selector=candidates.stream().map(r->r.get("map_marker_field_id")).filter(Objects::nonNull).findFirst().orElse(null);
            if(selector!=null) selector=fieldMap.getOrDefault(((Number)selector).longValue(),((Number)selector).longValue());
            execute("UPDATE service_option SET map_marker_field_id=? WHERE option_id=?",selector,id);
            execute("UPDATE service_type_option SET map_marker_field_id=? WHERE option_id=?",selector,id);
            if(candidates.stream().noneMatch(r->Objects.equals(owner(id),r.get("service_provider_type_id")))) {
                var restored=new LinkedHashMap<String,Object>();restored.put("option_id",id);restored.put("service_provider_type_id",owner(id));
                restored.put("display_order",option.getOrDefault("display_order",0));restored.put("status",option.get("status"));restored.put("map_marker_field_id",selector);insert("service_type_option",restored);
            }
        }
    }
    private void mergeDocuments() throws Exception {
        var groups=read("service_type_document_requirement").stream().filter(r->r.get("option_id")!=null)
                .collect(Collectors.groupingBy(r->r.get("option_id")+":"+r.get("document_type_id")));
        for(var group:groups.values()) {
            group.sort(priority("requirement_id","option_id"));var winner=new LinkedHashMap<>(group.get(0));
            winner.put("mandatory",group.stream().anyMatch(r->truth(r.get("mandatory"))));
            winner.put("requires_approval",group.stream().anyMatch(r->truth(r.get("requires_approval"))));
            for(int i=1;i<group.size();i++) delete("service_type_document_requirement","requirement_id",id(group.get(i),"requirement_id"));
            winner.put("service_provider_type_id",owner(id(winner,"option_id")));update("service_type_document_requirement","requirement_id",winner);
        }
    }
    private void mergeEligibility() throws Exception {
        var groups=read("customer_eligibility_rule").stream().filter(r->"OPTION".equals(r.get("scope"))).collect(Collectors.groupingBy(r->id(r,"option_id")));
        for(var entry:groups.entrySet()) {
            var group=entry.getValue();group.sort(Comparator.<Map<String,Object>>comparingInt(r->Objects.equals(owner(entry.getKey()),r.get("service_type_id"))?0:1)
                    .thenComparingLong(r->id(r,"rule_id")));
            var winner=new LinkedHashMap<>(group.get(0));var enabled=group.stream().filter(r->truth(r.get("enabled"))).toList();
            var codes=new LinkedHashSet<String>();for(var r:enabled) for(var n:json(r.get("required_codes_json"))) codes.add(n.asText());
            winner.put("enabled",!enabled.isEmpty());winner.put("required_codes_json",JSON.writeValueAsString(codes));
            var ages=enabled.stream().filter(r->r.get("minimum_age")!=null).sorted(Comparator.<Map<String,Object>>comparingInt(r->truth(r.get("requires_verified_date_of_birth"))?0:1)
                    .thenComparingInt(r->-number(r.get("minimum_age"),0))).toList();
            winner.put("minimum_age",ages.stream().mapToInt(r->number(r.get("minimum_age"),0)).max().isPresent()?ages.stream().mapToInt(r->number(r.get("minimum_age"),0)).max().getAsInt():null);
            winner.put("requires_verified_date_of_birth",ages.stream().anyMatch(r->truth(r.get("requires_verified_date_of_birth"))));
            winner.put("date_of_birth_code",ages.isEmpty()?null:ages.get(0).get("date_of_birth_code"));
            for(int i=1;i<group.size();i++) delete("customer_eligibility_rule","rule_id",id(group.get(i),"rule_id"));
            winner.put("service_type_id",owner(entry.getKey()));winner.put("target_key","OPTION:"+owner(entry.getKey())+":"+entry.getKey());update("customer_eligibility_rule","rule_id",winner);
        }
    }
    private Comparator<Map<String,Object>> priority(String key,String optionColumn) {
        return Comparator.<Map<String,Object>>comparingInt(r->Objects.equals(owner(id(r,optionColumn)),r.get("service_provider_type_id"))?0:1)
                .thenComparing(r->stamp(r,"updated_at"),Comparator.reverseOrder()).thenComparingLong(r->id(r,key));
    }

    private void recheckMergedEvidence() throws Exception {
        var definitions=read("service_custom_field");var applicability=new HashMap<Long,Set<Long>>();
        for(var r:read("service_custom_field_option")) applicability.computeIfAbsent(id(r,"field_id"),k->new HashSet<>()).add(id(r,"option_id"));
        var answers=read("provider_custom_field_submission");var docs=read("service_provider_document");
        var requirements=read("service_type_document_requirement");
        for(var registration:read("provider_service_option")) {
            if(!"APPROVED".equals(registration.get("verification_status"))) continue;
            long provider=id(registration,"provider_id"), option=id(registration,"option_id");boolean missing=false;
            for(var f:definitions) {
                if(!fieldMap.containsValue(id(f,"field_id")) || !"ACTIVE".equals(f.get("status"))
                        || !"PROVIDER_REQUIREMENT".equals(f.get("scope")) || !truth(f.get("required"))
                        || !applicability.getOrDefault(id(f,"field_id"),Set.of()).contains(option)) continue;
                if("PROVIDER_PROFILE".equals(f.get("value_source"))) {
                    var value=profileValue(f,provider);
                    if(value.isNull() || value.isTextual() && value.asText().isBlank()
                            || "SELECT".equals(f.get("field_type")) && !choiceExists(f,value.asText())) missing=true;
                    continue;
                }
                var condition=json(f.get("condition_json"));
                if(!condition.isNull() && !condition.isArray()) {
                    var parent=definitions.stream().filter(p->Objects.equals(p.get("code"),condition.path("fieldCode").asText())
                            && (applicability.getOrDefault(id(p,"field_id"),Set.of()).contains(option)
                                || applicability.getOrDefault(id(p,"field_id"),Set.of()).isEmpty()
                                    && Objects.equals(p.get("service_provider_type_id"),owner(option)))).findFirst();
                    JsonNode actual=NullNode.getInstance();
                    if(parent.isPresent()) {
                        var p=parent.get();
                        if("PROVIDER_PROFILE".equals(p.get("value_source"))) actual=profileValue(p,provider);
                        else {var answer=answers.stream().filter(r->Objects.equals(r.get("provider_id"),provider) && Objects.equals(r.get("field_id"),p.get("field_id"))).findFirst();
                            if(answer.isPresent()) actual=json(answer.get().get("value_json"));}
                    }
                    if(actual.isNull()) continue;
                    if(actual.has("value")) actual=actual.get("value");
                    boolean equal=actual.equals(condition.get("value"));
                    if("NOT_EQUALS".equals(condition.path("operator").asText("EQUALS"))?equal:!equal) continue;
                }
                var own=answers.stream().filter(r->Objects.equals(r.get("assignment_id"),registration.get("assignment_id"))
                        && Objects.equals(r.get("field_id"),f.get("field_id"))).findFirst();
                boolean satisfied=own.isPresent()? !truth(f.get("requires_approval")) || "APPROVED".equals(own.get().get("verification_status"))
                        : answers.stream().anyMatch(r->Objects.equals(r.get("provider_id"),provider)
                            && Objects.equals(r.get("field_id"),f.get("field_id")) && "APPROVED".equals(r.get("verification_status")));
                if(!satisfied) missing=true;
            }
            for(var req:requirements) {
                if(!Objects.equals(req.get("option_id"),option) || !truth(req.get("mandatory"))) continue;
                if(docs.stream().noneMatch(d->Objects.equals(d.get("service_provider_id"),provider)
                        && Objects.equals(d.get("document_type_id"),req.get("document_type_id"))
                        && (d.get("expiry_date")==null || d.get("expiry_date").toString().compareTo(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo")).toString())>=0)
                        && (!truth(req.get("requires_approval")) || "APPROVED".equals(d.get("verification_status"))))) missing=true;
            }
            if(missing) execute("UPDATE provider_service_option SET verification_status='PENDING', reviewed_by=NULL, reviewed_at=NULL WHERE provider_option_id=?",id(registration,"provider_option_id"));
        }
    }
    private JsonNode profileValue(Map<String,Object> field,long provider) throws Exception {
        var profile=read("service_provider").stream().filter(r->id(r,"service_provider_id")==provider).findFirst();
        if(profile.isEmpty()) return NullNode.getInstance();
        String name=text(field,"profile_field");Object value;
        if("age".equals(name)) {
            Object dob=profile.get().get("date_of_birth");
            value=dob==null?null:java.time.Period.between(java.time.LocalDate.parse(dob.toString()),java.time.LocalDate.now(java.time.ZoneId.of("Asia/Colombo"))).getYears();
        } else if("marketId".equals(name)) value=read("service_provider_market").stream()
                .filter(r->id(r,"service_provider_id")==provider && "ACTIVE".equals(r.get("status"))).map(r->r.get("market_id")).findFirst().orElse(null);
        else value=profile.get().get(name.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT));
        if(value instanceof java.util.Date) value=value.toString();
        return JSON.valueToTree(value);
    }

    private void finishSchema() throws SQLException {
        execute("ALTER TABLE provider_service_option MODIFY COLUMN provider_id BIGINT NOT NULL");
        boolean exists=false;
        try(var tables=db.getMetaData().getTables(db.getCatalog(),null,null,null)) {
            while(tables.next()) if("provider_service_option".equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                try(var indices=db.getMetaData().getIndexInfo(tables.getString("TABLE_CAT"),tables.getString("TABLE_SCHEM"),tables.getString("TABLE_NAME"),false,false)) {
                    while(indices.next()) if(Objects.toString(indices.getString("INDEX_NAME"),"").toLowerCase(Locale.ROOT).startsWith("uk_provider_shared_option")) exists=true;
                }
            }
        }
        if(!exists) execute("ALTER TABLE provider_service_option ADD CONSTRAINT uk_provider_shared_option UNIQUE(provider_id,option_id)");
        execute("UPDATE shared_option_merge_state SET status='COMPLETE' WHERE migration_key=?",KEY);
    }
    private void addColumn(String table,String column) throws SQLException {
        try(var rs=db.createStatement().executeQuery("SELECT * FROM "+q(table)+" WHERE 1=0")) {
            for(int i=1;i<=rs.getMetaData().getColumnCount();i++) if(column.equalsIgnoreCase(rs.getMetaData().getColumnName(i))) return;
        }
        execute("ALTER TABLE "+q(table)+" ADD COLUMN "+q(column)+" BIGINT NULL");
    }
    private void archive(String table,Map<String,Object> row) throws Exception {
        var archived=new TreeMap<String,Object>();row.forEach((k,v)->archived.put(k,v instanceof java.util.Date || v instanceof java.time.temporal.TemporalAccessor?v.toString():v));
        String payload=JSON.writeValueAsString(archived);
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        insert("shared_option_merge_archive",Map.of("migration_key",KEY,"table_name",table,"row_key",hash,"payload_json",payload));
    }
    private List<Map<String,Object>> read(String table) throws SQLException {
        var result=new ArrayList<Map<String,Object>>();
        try(var st=db.createStatement();var rs=st.executeQuery("SELECT * FROM "+q(table))) {
            while(rs.next()) {var row=new LinkedHashMap<String,Object>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++) {
                Object value=rs.getObject(i);if(value instanceof Clob c) value=c.getSubString(1,(int)c.length());
                row.put(rs.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT),value);
            }result.add(row);}
        }return result;
    }
    private void insert(String table,Map<String,Object> row) throws SQLException {
        String cols=row.keySet().stream().map(SharedOptionAutoMerge::q).collect(Collectors.joining(","));
        execute("INSERT INTO "+q(table)+" ("+cols+") VALUES ("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());
    }
    private void update(String table,String key,Map<String,Object> row) throws SQLException {
        var values=new ArrayList<Object>();var cols=new ArrayList<String>();
        row.forEach((k,v)->{if(!key.equals(k)){cols.add(q(k)+"=?");values.add(v);}});values.add(row.get(key));
        execute("UPDATE "+q(table)+" SET "+String.join(",",cols)+" WHERE "+q(key)+"=?",values.toArray());
    }
    private void delete(String table,String key,long id) throws SQLException { execute("DELETE FROM "+q(table)+" WHERE "+q(key)+"=?",id); }
    private void execute(String sql,Object...values) throws SQLException {try(var st=db.prepareStatement(sql)){for(int i=0;i<values.length;i++)st.setObject(i+1,values[i]);st.executeUpdate();}}
    private static Map<Long,Map<String,Object>> index(List<Map<String,Object>> rows,String key) {var result=new LinkedHashMap<Long,Map<String,Object>>();rows.forEach(r->result.put(id(r,key),r));return result;}
    private Long owner(long option) {var row=options.get(option);if(row==null)throw new IllegalArgumentException("Missing option "+option);return id(row,"service_provider_type_id");}
    private static long root(Map<Long,Long> parent,long id){long p=parent.get(id);if(p!=id)parent.put(id,p=root(parent,p));return p;}
    private static long id(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static int number(Object value,int fallback){return value instanceof Number n?n.intValue():fallback;}
    private static boolean truth(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static String stamp(Map<String,Object> row,String key){return text(row,key);}
    private static double decimal(Object value){return value==null?0:new BigDecimal(value.toString()).doubleValue();}
    private static JsonNode json(Object value) throws Exception {return value==null?JSON.createArrayNode():JSON.readTree(value.toString());}
    private static String q(String name){if(!name.matches("[a-z_][a-z0-9_]*"))throw new IllegalArgumentException("Invalid SQL identifier");return "`"+name+"`";}
}
