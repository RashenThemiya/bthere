# Service configuration discussion template

Open **Service-Configuration-Workshop.drawio** in draw.io / diagrams.net:

1. Use **File → Open From → Device** and choose the `.drawio` file.
2. Switch pages using the tabs at the bottom.
3. Right-click **02 — Blank setup worksheet → Duplicate** for each service option.
4. Double-click a card to replace the underscores with your decisions.
5. Save the editable file; use **File → Export as → PDF** for a meeting handout.

Pages:

| Page | Discussion purpose |
|---|---|
| 01 | Main service, shared option, provider and customer relationships |
| 02 | Blank configuration worksheet to duplicate and fill |
| 03 | Actual enum values, restrictions and status definitions from backend code |
| 04 | Provider requirements, document parts, booking questions and reuse |
| 05 | Time/route prices, conditional combinations, areas and map markers |
| 06 | Filled Care Ride example, including provider/customer workflows |
| 07 | Proposed global field catalog and profile-choice reuse design |
| 08 | Common service policies, customer eligibility and rollout checklist |

Blue means existing local backend configuration; orange means proposed, not
implemented. Yellow is for decisions; green is an illustrative example. Deployment
is not verified by this chart. Payment/matching enums do not guarantee working
production payment/invitation flows. Example IDs and prices are not live settings.

The diagram is native uncompressed draw.io XML, not an embedded screenshot. Every
card, label and connector is editable. Regenerate it with:

```text
python docs/diagrams/build_service_workshop.py
```

Regeneration overwrites the generated file; save a separate workshop copy before
recording meeting decisions.

Sources: `ServiceSetupRequest`, `CreateServiceOptionRequest`,
`ServiceCustomFieldRequest`, `ServiceOptionService`, `ProviderProfileFields`,
`ServicePricingService`, `DocumentPartService`, `CreateBookingRequest`, and
the local `FULLY_SHARED_OPTIONS_API.md` / `DOCUMENT_PARTS_API.md` contracts.
