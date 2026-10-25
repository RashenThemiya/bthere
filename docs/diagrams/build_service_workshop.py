"""Generate an uncompressed, editable diagrams.net workshop document (stdlib only)."""
from pathlib import Path
import html
import xml.etree.ElementTree as ET

OUT = Path(__file__).with_name('Service-Configuration-Workshop.drawio')
COLORS = {
    'current': ('#eff6ff', '#2563eb'),
    'proposed': ('#fff7ed', '#ea580c'),
    'decision': ('#fefce8', '#ca8a04'),
    'example': ('#f0fdf4', '#16a34a'),
    'note': ('#f1f5f9', '#64748b'),
}
doc = ET.Element('mxfile', host='app.diagrams.net', agent='CareHub workshop generator', version='24.7.17', type='device')

class Page:
    def __init__(self, name, subtitle, height=1900):
        self.diagram=ET.SubElement(doc,'diagram',id='page-'+str(len(doc)+1),name=name)
        model=ET.SubElement(self.diagram,'mxGraphModel',dx='1600',dy='1000',grid='1',gridSize='10',guides='1',tooltips='1',connect='1',arrows='1',fold='1',page='1',pageScale='1',pageWidth='1600',pageHeight=str(height),math='0',shadow='0')
        self.root=ET.SubElement(model,'root')
        ET.SubElement(self.root,'mxCell',id='0')
        ET.SubElement(self.root,'mxCell',id='1',parent='0')
        self.n=1
        self.box(name, [subtitle],40,25,1520,110,'note',font=22)
        self.box('LEGEND / STATUS', ['BLUE = existing backend configuration (local code; deployment not verified)', 'ORANGE = proposed design, not implemented | YELLOW = discussion / fill in | GREEN = example only', 'Editable workshop template • 23 Oct 2026 • IDs, prices and examples are illustrative'],40,150,1520,110,'note',font=14)

    def box(self,title,lines,x,y,w=480,h=265,kind='current',font=16):
        self.n+=1; ident='n'+str(self.n)
        fill,stroke=COLORS[kind]
        label='<div style="text-align:left;padding:12px"><b>'+html.escape(title)+'</b><br><br>'+'<br>'.join(html.escape(t) for t in lines)+'</div>'
        cell=ET.SubElement(self.root,'mxCell',id=ident,value=label,style=f'rounded=1;whiteSpace=wrap;html=1;fillColor={fill};strokeColor={stroke};strokeWidth=2;fontFamily=Arial;fontSize={font};fontColor=#0f172a;verticalAlign=top;align=left;spacing=4;arcSize=8;',vertex='1',parent='1')
        ET.SubElement(cell,'mxGeometry',x=str(x),y=str(y),width=str(w),height=str(h),**{'as':'geometry'})
        return ident

    def edge(self,a,b,label=''):
        self.n+=1
        cell=ET.SubElement(self.root,'mxCell',id='e'+str(self.n),value=label,style='edgeStyle=orthogonalEdgeStyle;rounded=1;html=1;endArrow=block;strokeColor=#64748b;strokeWidth=2;fontSize=13;labelBackgroundColor=#ffffff;',edge='1',parent='1',source=a,target=b)
        ET.SubElement(cell,'mxGeometry',relative='1',**{'as':'geometry'})

    def cards(self,cards,cols=3,h=275):
        for i,card in enumerate(cards):
            title,lines,*kind=card
            self.box(title,lines,40+(i%cols)*515,300+(i//cols)*(h+35),490,h,kind[0] if kind else 'current')

p=Page('01 — System & ownership map','Start here: decide what belongs to the main service, shared option, provider and booking.',1900)
a=p.box('MAIN SERVICE — navigation + common setup',['Examples: Caregiver / Ride / Cleaning','Identity: serviceTypeId, name, description','Media: icon, cover, gallery, marker default','Common registration: availability, areas, skills,','certificates, education, common documents','Option selection: required / min / max'],40,300,490,285)
b=p.box('SERVICE ↔ OPTION LINK',['One shared optionId can appear in many services.','Example: Care Ride → Ride AND Caregiver','Link settings: displayOrder + status','Do not copy option fields or prices per link.','Main-service common policies remain distinct.'],555,300,490,285)
c=p.box('SHARED OPTION — reusable configuration',['Identity + media + responsibilities','Scheduling / delivery / fulfillment / capacity','Provider fields + booking fields + document rules','Payment configuration + price ownership','Conditional rates + combination marker rules','Historical owner = configuration namespace'],1070,300,490,285)
p.edge(a,b,'contains links');p.edge(b,c,'references one optionId')
d=p.box('PROVIDER REGISTRATION',['Provider selects option and supplies capabilities.','Requirements: answers + documents + reviews','Operational source: availability + areas + location','One provider enrollment per shared option','Find shared registration; do not register again','just because another main service links to it.'],40,670,490,285)
e=p.box('CUSTOMER WORKFLOW',['Service → option → booking schema','Select time OR route + delivery details','Apply configured provider filters','Search eligible providers / configured team','Quote → select payment → create booking','Booking answers belong to the customer job.'],555,670,490,285)
f=p.box('PRICING + MAP PRESENTATION',['Rate conditions reference fieldId + choiceValue.','CAR + MALE can have a specific fare.','Marker rules use a combination of choices.','Service icon ≠ map marker icon.','Shared option means consistent rules from','each linked service entry point.'],1070,670,490,285)
p.edge(c,d,'registration contract');p.edge(c,e,'customer schema');p.edge(c,f,'shared rules');p.edge(d,e,'eligible candidates')
p.box('EXAMPLE RELATIONSHIP',['Ride (service 2) ─┐','                  ├─ Care Ride (option 4): ONE shared definition','Caregiver (1) ────┘','Vehicle Type + Licence requirements + Passenger Count + CAR pricing/marker rules are reused.'],40,1060,1005,220,'example')
p.box('DISCUSSION DECISIONS',['Service list: ______________________','Shared option list: _________________','Who configures prices? ______________','Provider registration owner/source? ___','Decision owner / date: ______________'],1070,1060,490,220,'decision')
p.box('BOUNDARY / AVOID ASSUMPTIONS',['The proposed global FIELD catalog is a separate future layer (page 07). Shared OPTION configuration already exists locally.','An enum being accepted does not prove the complete matching/payment workflow is deployed.','Existing Front/Back document types are preserved; they are not automatically consolidated by name.'],40,1340,1520,190,'note')

p=Page('02 — Blank setup worksheet','Duplicate this page for each option. Replace underscores during the discussion.',1810)
p.cards([
('01 / MAIN SERVICE',['Name / serviceTypeId: ______________','Description / audience: ______________','Common requirements: ______________','Option min / max selections: _________','Availability / areas enabled: _________'],'decision'),
('02 / OPTION & LINKS',['Code / name / optionId: ______________','Original configuration service: _______','Linked main services: _______________','Link order / active status: ___________','Description / responsibilities: _________'],'decision'),
('03 / DELIVERY & LOCATION',['deliveryModes: _____________________','locationEnabled: ___________________','Provider address / meeting point: _____','Coverage centre + radius: ____________','Online or physical workflow: __________'],'decision'),
('04 / SCHEDULING',['schedulingModel: __________________','TIME: start / end / duration: __________','ROUTE: pickup / destination: __________','Route average speed: _______________','Availability constraints: ______________'],'decision'),
('05 / CAPACITY & SELECTION',['fulfillmentModel: ___________________','defaultCapacity: ___________________','requiredProviderCount: ______________','bookingMode: _____________________','providerSelectionMode: ______________'],'decision'),
('06 / PROVIDER REQUIREMENTS',['Field codes / types: _________________','MANUAL or PROVIDER_PROFILE: ______','Required / approval rules: ____________','reuseScope per field: ________________','Filter-visible choices: _______________'],'decision'),
('07 / DOCUMENTS & PARTS',['Document type(s): __________________','Parts: FRONT / BACK / other: _________','Required + count + MIME + size: ______','Issued/expiry date modes: ____________','Bundle review required? ______________'],'decision'),
('08 / BOOKING TEMPLATE',['Customer fields / types: ______________','Required / optional: _________________','Conditional questions: _______________','Shared option scope: ________________','Example completed booking: __________'],'decision'),
('09 / PRICING & PAYMENTS',['pricingOwner: _____________________','billingType / durationMinutes: _________','Rate or base/per-km/minimum: _________','Choice combinations / market / area: ___','Allowed methods / actual integration: ___'],'decision'),
('10 / MEDIA & MAP',['Service / option icon: ________________','Cover / gallery: _____________________','Default marker / choice marker: _______','Combination conditions → icon: _______','Fallback / unmatched cases: __________'],'decision'),
('11 / SEARCH & QUOTE',['Public provider filters: _______________','Candidate attributes: ________________','Price selection combinations: _________','Quote totals / payment behavior: ______','Booking recovery / status UX: _________'],'decision'),
('12 / SIGN-OFF / OPEN ITEMS',['Unresolved questions: _______________','Backend / frontend owner: ____________','Migration / compatibility plan: _________','Acceptance example + expected result: __','Decision / next action / date: __________'],'decision'),
],h=290)

p=Page('03 — Configuration enums','Values below are from local backend validation. Configuration support is not a deployment guarantee.',2250)
p.cards([
('schedulingModel',['TIME_BASED — reserve a time window','ROUTE_BASED — pickup + destination','Route restriction: locationEnabled=true','deliveryModes=[PROVIDER_TO_CUSTOMER]','fulfillmentModel=ONE_TO_ONE']),
('deliveryModes (set)',['PROVIDER_TO_CUSTOMER','CUSTOMER_TO_PROVIDER','ONLINE','Online-only cannot enable physical locations.']),
('fulfillmentModel',['ONE_TO_ONE','MANY_CUSTOMERS_ONE_PROVIDER','ONE_CUSTOMER_MANY_PROVIDERS','Capacity uses defaultCapacity or','requiredProviderCount as applicable.']),
('bookingMode',['ONE_AT_A_TIME','MANY_AT_A_TIME','Choose the business concurrency behavior.','Do not equate this with fulfillmentModel.']),
('providerSelectionMode',['DIRECT_REQUEST','OPEN_REQUEST','These are accepted configuration values.','Verify invitations / acceptance / matching','implementation before promising that UX.']),
('pricingOwner / billingType',['pricingOwner: ADMIN | PROVIDER','TIME billing: HOURLY | DAILY | FIXED','ROUTE billing: ROUTE_BASED','Rates also have effective windows, market,','optional centre/radius and conditions.']),
('scope / reuseScope',['scope: PROVIDER_REQUIREMENT','or BOOKING_FIELD','reuseScope: ASSIGNMENT | SERVICE | PROVIDER','BOOKING_FIELD only allows ASSIGNMENT.','Same shared field identity also enables reuse.']),
('fieldType',['TEXT | TEXTAREA | NUMBER | BOOLEAN','DATE | TIME | PHONE | EMAIL','SELECT | MULTI_SELECT','IMAGE_UPLOAD | DOCUMENT_UPLOAD','FILE_UPLOAD']),
('valueSource / profileField',['MANUAL | PROVIDER_PROFILE','Profile: firstName, lastName, nicNumber,','passportNumber, dateOfBirth, age, gender,','profilePhoto, bio, marketId','Profile fields cannot require approval.']),
('Public filters / operators',['NUMBER → RANGE','BOOLEAN → EQUALS','SELECT / MULTI_SELECT → IN','Searchable implies customerVisible.','Only profile gender/age can be published.']),
('allowedPaymentMethods (set)',['CASH | CARD | BANK_TRANSFER','WALLET | EZ_CASH | KOKO','Configured method ≠ working production gateway.','Verify actual processing separately.']),
('Section / date modes',['Certificates / education level:','DISABLED | OPTIONAL | REQUIRED','Issued/expiry date modes:','HIDDEN | OPTIONAL | REQUIRED']),
('Review / configuration status',['Configuration: ACTIVE | INACTIVE','Document / requirement review:','PENDING | APPROVED | REJECTED','Profile-resolved value status:','PROVIDED | MISSING | INVALID']),
('Documents: part configuration',['code: uppercase unique identifier','label; required; maximumFiles: 1–100','maximumFileSizeMb: 1–100','MIME: image/jpeg, image/png,','image/webp, application/pdf']),
('SOURCE / PLANNED ITEMS',['Sources: ServiceOptionService;','ServiceCustomFieldService; ProviderProfileFields;','ServicePricingService; DocumentPartService.','Global field linking + auto profile choices:','PROPOSED, not part of these current enums.'],'note'),
],h=325)

p=Page('04 — Provider & booking templates','Two different form scopes; one shared option. Documents are reviewed as complete bundles.',1720)
p.cards([
('A / PROVIDER_REQUIREMENT',['Admin configures: code, label, type, choices','required, requiresApproval, reuseScope','Optional profile source / conditions / icons','Customer visibility + searchable operator','Example: vehicle type / driving experience']),
('B / PROVIDER ANSWERS',['Provider registers for the shared option.','Vehicle Type = CAR','Gender resolves from profile if configured.','Profile-backed fields are read-only here.','Exact answers take priority over reusable ones.']),
('C / REQUIREMENT REUSE',['ASSIGNMENT: registration-level behavior','SERVICE: compatible approved answers','within the same service','PROVIDER: across services for that provider','Different fields require matching code + type.']),
('D / DOCUMENT TYPE TEMPLATE',['Name: Driving Licence','Parts: FRONT (required), BACK (required)','Optional EXTRA: multiple files','Each part: MIME / max files / max size','Dates: issued OPTIONAL, expiry REQUIRED']),
('E / SUBMISSION & REVIEW',['Upload each file privately → submit keys','POST one complete document bundle','Validate ownership, required parts, MIME, size','One bundle-level approval; replacement → PENDING','Existing uploads and approvals are preserved.']),
('F / DOCUMENT COMPATIBILITY',['Old single-file type: keep documentKey','Multipart type: send parts with documentKeys','Store partDefinitions snapshot per submission','Admin config changes apply next submission.','Old Front/Back types are not auto-combined.']),
('G / BOOKING_FIELD TEMPLATE',['Customer-facing question for this option','Example: Passenger Count → NUMBER','Example: Assistance Notes → TEXTAREA','Hospital Ward / Bed can be configured fields.','Conditions control when a question applies.']),
('H / BOOKING REQUEST SHAPE',['serviceTypeId + shared optionId','TIME: startDatetime + expectedEndDatetime','ROUTE: requestedPickupTime + points','answers: [{fieldId, value}]','pricingSelections: [{fieldId, choiceValue}]']),
('I / DISCUSSION CHECKLIST',['Which fields must be provider capabilities? ___','Which are customer booking questions? ______','Which are profile values? __________________','Which need separately reviewed proof? ______','Do not collect the same value twice.'],'decision'),
],h=365)
for a,b in [('n4','n5'),('n7','n8'),('n10','n11')]:
    p.edge(a,b)

p=Page('05 — Price variants & map rules','A variant is a combination of configured choice values, not a duplicate service option.',1840)
p.cards([
('01 / SELECT THE CONTEXT',['Shared option: CARE_RIDE','Market: configured market ID','pricingOwner: ADMIN or PROVIDER','schedulingModel: ROUTE_BASED','Rates reused through each main-service link.']),
('02 / DEFINE CAPABILITY FIELDS',['VEHICLE_TYPE: CAR / BIKE / VAN / TUK_TUK','DRIVER_GENDER: MALE / FEMALE / OTHER','Choice conditions use actual fieldId values.','Use compatible public provider choice fields.','Do not create a new option for every pair.']),
('03 / CONDITION COMBINATION',['Example conditions:','[{fieldId: 5, choiceValue: CAR},',' {fieldId: 8, choiceValue: FEMALE}]','Conditions are combined with AND.','Compare actual provider attributes/selections.'],'example'),
('04 / ROUTE PRICE TEMPLATE',['billingType: ROUTE_BASED','baseFare: _____  pricePerKm: _____','minimumFare: _____  market: _____','fare = max(minimumFare,','baseFare + pricePerKm × estimatedDistanceKm)']),
('05 / TIME PRICE TEMPLATE',['HOURLY: rate × ceil(minutes / durationMinutes)','durationMinutes defaults to 60 when absent.','DAILY: rate × ceil(minutes / 1440)','FIXED: configured rate for the booking','This is not tree-count or arbitrary unit billing.']),
('06 / AREA + EFFECTIVE WINDOW',['Optional area: name + centre + radius km','effectiveFrom / effectiveTo: rate validity','Different areas can have different rates.','Pickup-zone + destination-zone fare matrix','is not an existing configured feature.']),
('07 / SERVICE ICON ≠ MAP MARKER',['Service/option icon: catalog presentation','Map marker: provider/vehicle on search map','Option default marker can be configured.','Choice/combination rule → mapMarkerIcon','CAR + FEMALE can point to a distinct asset.']),
('08 / EXAMPLE RULE SHEET',['CAR + MALE → base 200 / km 100 / min 500','CAR + FEMALE → base 250 / km 110 / min 600','BIKE → base 100 / km 60 / min 250','CAR + FEMALE → car marker asset','Illustrative numbers only; choose currency/market.'],'example'),
('09 / VALIDATION DISCUSSION',['Unmatched combination policy: __________','Overlapping rules / specificity: __________','Fallback marker: _____________________','Quote example + expected amount: ______','Select only real backend-supported behavior.'],'decision'),
('10 / ROUTE ESTIMATE LIMIT',['Current route estimate uses coordinate distance','and configured average speed.','Do not describe it as traffic-aware navigation','or live vehicle tracking.','Confirm road-routing integration separately.'],'note'),
('11 / PRICE SOURCE OF TRUTH',['Server calculates the quote / booking price.','Frontend sends choice IDs/values, not final fare.','Keep provider selection and pricing conditions','consistent through quote and booking.','Historical booking price snapshots stay intact.']),
('12 / UNIT-BASED IDEAS',['Tree count / sack count / per-item billing','need an explicit unit-price feature design.','A booking question alone does not multiply fare.','For now decide whether supported TIME or','FIXED pricing fits the actual business case.'],'decision'),
],h=310)

p=Page('06 — Filled example: Care Ride','Use this example to discuss a real setup. All IDs, choices and prices are illustrative.',1840)
p.cards([
('01 / MAIN SERVICES',['Caregiver: serviceTypeId 1','Ride: serviceTypeId 2','Both link to CARE_RIDE optionId 4.','Do not create two Care Ride definitions.'],'example'),
('02 / SHARED OPTION',['code: CARE_RIDE','schedulingModel: ROUTE_BASED','deliveryModes: [PROVIDER_TO_CUSTOMER]','locationEnabled: true','fulfillmentModel: ONE_TO_ONE'],'example'),
('03 / OPERATIONS',['bookingMode: ONE_AT_A_TIME','providerSelectionMode: DIRECT_REQUEST','requiredProviderCount: 1','Coverage: centre + radius','Provider defines availability and coverage.'],'example'),
('04 / PROVIDER FORM',['Vehicle Type → SELECT → CAR / BIKE / VAN','Gender → provider-profile gender','Experience → NUMBER (optional)','Driver licence → document requirement','Publish searchable attributes deliberately.'],'example'),
('05 / BOOKING TEMPLATE',['Passenger Count → NUMBER → required','Needs mobility assistance → BOOLEAN','Assistance Notes → TEXTAREA → optional','Pickup + destination + requested pickup time','Use the actual returned field IDs.'],'example'),
('06 / PROOF CONFIGURATION',['Driving Licence: FRONT + BACK required','Accepted files: JPEG / PNG; 5 MB each','Expiry date REQUIRED','Bundle approval required','Existing separately stored proof stays intact.'],'example'),
('07 / PRICING',['ADMIN-owned route rates','CAR + FEMALE: base 250 / km 110 / min 600','At estimated 5 km → max(600,250+550)=800','Currency/market to be agreed','No client-supplied final price.'],'example'),
('08 / MAP PRESENTATION',['Catalog icon: Care Ride category icon','Default marker: vehicle marker','CAR + FEMALE: configured car marker','BIKE: configured bike marker','Do not imply live tracking is enabled.'],'example'),
('09 / PAYMENTS',['Example allowed methods: CASH, WALLET','Add CARD only with agreed processing behavior.','Accepted enum does not confirm live processing.','Confirm fees / refund / cancellation contract','before customer workflow sign-off.'],'example'),
('10 / PROVIDER JOURNEY',['Choose service → choose CARE_RIDE','Load shared requirements → complete profile','Submit capabilities + complete proof bundle','Review / approval → set operational availability','Same enrollment reused from linked services.'],'example'),
('11 / CUSTOMER JOURNEY',['Caregiver OR Ride → CARE_RIDE','Enter route/time + booking answers','Filter CAR + FEMALE → search candidates','Quote → select provider → create booking','Uses the same underlying option/rate rules.'],'example'),
('12 / REVIEW QUESTIONS',['Should VAN support more passengers? ______','Is one option enough for all vehicle types? ___','Required gender filter or optional? __________','Which proof is mandatory? _________________','Business owner / approval date: ____________'],'decision'),
],h=310)

p=Page('07 — Proposed global field catalog','Future discussion model: define a field once, then link it to options. Not implemented.',1810)
a=p.box('GLOBAL FIELD DEFINITION — proposed',['fieldId + code + label + type','Choices + icons + value source + profile field','Example: PROVIDER_GENDER','One canonical definition; no per-link type overrides','Changing global choices affects all its links.'],40,300,490,300,'proposed')
b=p.box('OPTION ↔ FIELD LINK — proposed',['optionId + fieldId','required + displayOrder','Link / unlink without deleting the global field','Keep link settings minimal at first.','Search visibility / review policy placement:','decide explicitly before implementation.'],555,300,490,300,'proposed')
c=p.box('OPTION USAGE — proposed',['CARE_RIDE → Gender + Vehicle Type','CAREGIVING → Gender + Experience','CLEANING → Gender if needed','Reusing a definition is separate from','reusing a provider answer.'],1070,300,490,300,'proposed')
p.edge(a,b,'link definition');p.edge(b,c,'effective schema')
p.box('PROFILE FIELD PICKER — proposed',['Select valueSource = PROVIDER_PROFILE','Select profileField = gender','Auto-load canonical profile choices','Admin must not maintain a second gender enum.','Read the provider value from their profile.','Current code still needs explicit SELECT choices.'],40,700,490,300,'proposed')
p.box('ANSWER REUSE — existing concept',['Keep existing reuseScope semantics separate.','ASSIGNMENT / SERVICE / PROVIDER','Use profile source for existing profile values.','An unrelated question with a similar label','must not automatically share answers.','Migration needs explicit equivalence mappings.'],555,700,490,300,'current')
p.box('SIMPLE FIRST VERSION — proposed',['No per-option choice/type/icon overrides.','Need different choices? Define another field.','Link-local required + order only.','Decide one place for approval/filter policies.','Preserve scope split: provider vs booking.','Avoid an inheritance engine for every property.'],1070,700,490,300,'proposed')
p.box('MIGRATION CHECKLIST',['Map existing field IDs → canonical global IDs','Preserve answers/reviews and price/marker references','Preserve historical bookings; reload frontend schemas','Archive superseded definitions; never invent approvals','Do not infer equivalence from matching labels alone.'],40,1100,1005,280,'decision')
p.box('OPEN DECISIONS / SIGN-OFF',['Global field ownership: ______________','Review policy location: ______________','Search flags location: _______________','Rollout/migration owner: ______________','Approved design / date: ______________'],1070,1100,490,280,'decision')
p.box('WORKSHOP ORDER',['1. Fill service/option worksheet → 2. Choose enums → 3. Separate provider and booking questions → 4. Agree proofs','5. Walk through price + marker combinations → 6. Validate one complete provider/customer example → 7. Decide global catalog scope.'],40,1450,1520,160,'note')

p=Page('08 — Common policies & readiness','Complete the remaining main-service setup and customer eligibility decisions before implementation sign-off.',1750)
p.cards([
('MAIN-SERVICE SELECTION RULES',['availabilityEnabled / serviceAreasEnabled','optionsRequired + minimumOptionSelections','maximumOptionSelections','Common policies are not copied per option link.','Selected option configuration stays shared.']),
('COMMON PROVIDER REQUIREMENTS',['documents: type ID + required + review flag','skills: name + required + review flag','certificates / education: level + review flag','Level: DISABLED | OPTIONAL | REQUIRED','Keep service-common and option-specific distinct.']),
('RESPONSIBILITIES & MEDIA',['responsibleParty: PROVIDER | CUSTOMER | PLATFORM','Each responsibility: title, description, required, order','Service and option: icon / cover / gallery','Marker is a separate presentation asset.','Provider languages: SINHALA | ENGLISH | TAMIL']),
('CUSTOMER REUSABLE REQUIREMENTS',['Separate from provider requirements/booking fields.','Definition: code / type / required / targets','verificationMode: NONE | ADMIN_REVIEW','or DOCUMENT_REVIEW','Evidence codes, choices, file limits, blocksBooking']),
('CUSTOMER ELIGIBILITY POLICY',['enabled + minimumAge + dateOfBirthCode','requiresVerifiedDateOfBirth + requiredCodes','Choose service / shared-option targeting deliberately.','Existing service-common gates remain separate.','Shared option policy follows canonical context.']),
('THREE DIFFERENT DATA COLLECTIONS',['Provider requirement: provider capability / proof','Customer reusable requirement: identity / eligibility','BOOKING_FIELD: question for this particular job','Example: licence vs customer identity vs ward/bed','Do not force all three into one form.'],'note'),
('COMPATIBILITY & MIGRATION',['Preserve existing option and field references.','Keep provider documents/reviews; archive merges.','Refresh clients to canonical field IDs after rollout.','Document schema columns must exist before deploy.','Do not claim production migration has been run.']),
('FRONTEND ACCEPTANCE CHECK',['Load effective shared fields without owner-ID rejection.','Use returned IDs / field types / choices / scope.','Render all document parts, not only first documentKey.','Do not hardcode per-service booking questions.','Display fallback map marker when applicable.']),
('WORKSHOP RELEASE CHECKLIST',['Chosen service option template: ______________','One complete provider/customer example: ______','Backend gaps / proposed features: ____________','Payments/matching/deployment verification: ____','Decision owner / next action / date: ___________'],'decision'),
],h=350)

ET.indent(doc, space='  ')
OUT.write_bytes(ET.tostring(doc,encoding='utf-8',xml_declaration=True))
print(f'Created {OUT.name}: {len(doc)} editable pages')

if __name__ == '__main__':
    parsed=ET.parse(OUT)
    for diagram in parsed.findall('diagram'):
        cells=diagram.findall('.//mxCell'); ids={c.get('id') for c in cells}
        assert len(ids)==len(cells), 'Duplicate cell ID'
        for cell in cells:
            if cell.get('edge')=='1':
                assert cell.get('source') in ids and cell.get('target') in ids, 'Dangling connector'
    print('Validated XML, unique node IDs and all connector references.')
