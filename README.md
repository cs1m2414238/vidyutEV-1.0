# Vidyut EV charging platform

Vidyut is a development-stage EV charging ecosystem for Indian drivers, charging Hosts, charging-network companies, and platform administrators. It combines connector-aware road-trip planning, charging and booking workflows, property-to-company partnerships, live charger operations, and scoped AI assistance.

> Important: district hubs, corridor hubs, companies, vehicles, prices, ratings, finance figures, and government-assistance leads included by the demo seeders are synthetic. They demonstrate workflows and must not be presented as verified commercial stations, offers, or scheme eligibility.

## Repository structure

| Module | Stack | Responsibility |
| --- | --- | --- |
| [`vidyut-backend`](./vidyut-backend) | Java 17, Spring Boot 3.3.7, PostgreSQL, Flyway, JWT | Authentication, role boundaries, stations, vehicles, bookings, payments, Autopilot, Host/Company/Admin operations, and demo seeders |
| [`vidyut-web`](./vidyut-web) | React 19, TypeScript 6, Vite 8, Leaflet | EV Owner, Host, Company, and separate Admin workspaces |
| [`vidyut-ai/agent`](./vidyut-ai/agent) | Python 3.10+, Google ADK, Gemini, FastAPI, OpenRouter fallback | Natural-language EV agent and backend tool orchestration |
| [`vidyut-mobile`](./vidyut-mobile) | React Native 0.86, Expo 57, Expo Router | Android/iOS owner, Host, Company, and Admin flows with BLE and offline support |

```text
Web / Mobile
     │ mode-scoped JWT
     ▼
Spring Boot API ───── PostgreSQL
     │                    │
     ├── OSRM + geocoder  ├── trips, stations, sessions
     │                    └── audit and marketplace state
     ▼
Python ADK agent ─── Gemini or OpenRouter
```

## System flow diagrams

All diagrams use one semantic palette: **Vidyut green** for healthy platform flow and successful outcomes, **sky blue** for people and inputs, **violet** for AI and governance, **amber** for decisions, **orange** for degraded fallbacks, and **rose** for faults or blocked actions.

### 1. Platform architecture and responsibility boundaries

This view shows which workspace owns each action, which backend domain performs it, and where live infrastructure and external intelligence enter the system.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TB
    subgraph EXPERIENCE["Role-scoped experiences"]
        OWNER["EV Owner<br/>garage • route plan • booking • live journey"]
        HOST["Property Host<br/>listings • occupancy • revenue • maintenance"]
        COMPANY["Charging Company<br/>stations • partnerships • pricing • service"]
        ADMIN["Admin control plane<br/>verification • incidents • scoped intervention"]
    end

    subgraph CORE["Spring Boot domain API"]
        API["REST API<br/>validation • authorization • safe error responses"]
        AUTH["Identity and access<br/>JWT • role • account state"]
        AUTOPILOT["Autopilot<br/>intent • route • charging • feasibility"]
        OPERATIONS["Operations<br/>booking • session • wallet • payment"]
        MARKETPLACE["Marketplace<br/>property • survey • proposal • installation"]
        GOVERNANCE["Governance<br/>evidence • audit • support • operational controls"]
        NOTIFY["Notifications<br/>trip • booking • fault • review updates"]
    end

    subgraph INTELLIGENCE["AI and routing integrations"]
        AGENT["Python ADK agent<br/>read-only tools + confirmed actions"]
        MODEL["Model provider<br/>Gemini → OpenRouter → deterministic fallback"]
        OSRM["Road intelligence<br/>primary OSRM → reference OSRM"]
        GEOCODER["Location resolution<br/>known aliases → geocoder"]
    end

    subgraph DATA_LAYER["Persistent and seeded data"]
        DB[("PostgreSQL<br/>accounts • assets • trips • money • audit")]
        MIGRATIONS["Flyway migrations<br/>schema and operational controls"]
        DEMO["Demo network<br/>112 corridor + 777 district stations"]
    end

    subgraph FIELD["Live charging infrastructure"]
        STATION["Charging station<br/>property hosted • company operated"]
        CONNECTOR["Connector<br/>CCS2 • CHAdeMO • GB/T • Type 2"]
        TELEMETRY["Live status<br/>available • occupied • load • queue • fault"]
        SESSION["Charging session<br/>energy • cost • payment • receipt"]
    end

    OWNER --> API
    HOST --> API
    COMPANY --> API
    ADMIN --> API
    API --> AUTH
    API --> AUTOPILOT
    API --> OPERATIONS
    API --> MARKETPLACE
    API --> GOVERNANCE
    API --> NOTIFY
    API <--> AGENT
    AGENT --> MODEL
    AUTOPILOT --> OSRM
    AUTOPILOT --> GEOCODER
    AUTH --> DB
    AUTOPILOT --> DB
    OPERATIONS --> DB
    MARKETPLACE --> DB
    GOVERNANCE --> DB
    NOTIFY --> DB
    MIGRATIONS --> DB
    DEMO --> DB
    DB --> STATION
    STATION --> CONNECTOR
    CONNECTOR --> TELEMETRY
    TELEMETRY --> OPERATIONS
    OPERATIONS --> SESSION
    TELEMETRY --> AUTOPILOT

    classDef actor fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef platform fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef intelligence fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef data fill:#ecfccb,stroke:#65a30d,color:#365314,stroke-width:2px
    classDef field fill:#fff7ed,stroke:#ea580c,color:#7c2d12,stroke-width:2px
    class OWNER,HOST,COMPANY,ADMIN actor
    class API,AUTH,AUTOPILOT,OPERATIONS,MARKETPLACE,GOVERNANCE,NOTIFY platform
    class AGENT,MODEL intelligence
    class DB,MIGRATIONS,DEMO,OSRM,GEOCODER data
    class STATION,CONNECTOR,TELEMETRY,SESSION field
```

### 2. Natural-language Autopilot planning and authority

The optimization strategy selects the best safe plan; the autonomy mode independently decides whether Vidyut may execute it.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TD
    subgraph REQUEST["Journey request"]
        TEXT["Natural-language text<br/>route • reserve • budget • arrival • priorities"]
        PARSE["Parse supported intent<br/>without inventing missing locations or limits"]
        FORM["Explicit controls<br/>vehicle • SoC • reserve • budget • arrive-by"]
        STRATEGY["Optimization strategy<br/>Fastest • Balanced • Lowest cost"]
        TEXT --> PARSE
        PARSE --> MERGE["Merge parsed values with explicit controls<br/>explicit fields remain authoritative"]
        FORM --> MERGE
    end

    subgraph CONTEXT["Verified planning context"]
        VEHICLE["Vehicle profile<br/>usable kWh • Wh/km • charging loss"]
        CURVE["Charging capability<br/>connector • max DC kW • SoC curve"]
        ROAD["Road route<br/>distance • duration • geometry • provenance"]
        STATIONS["Compatible corridor stations<br/>price • power • queue • reliability • status"]
        MATRIX["Station route matrix<br/>detour distance and time for each candidate"]
    end

    MERGE --> VEHICLE
    VEHICLE --> CURVE
    MERGE --> ROAD
    ROAD --> STATIONS
    STATIONS --> MATRIX
    CURVE --> OPTIMIZE["Optimize reachable charging sequence<br/>drive + detour + queue + setup + charge"]
    MATRIX --> OPTIMIZE
    STRATEGY --> OPTIMIZE

    OPTIMIZE --> CHARGE["Integrate energy across charging-curve segments<br/>effective kW = min(charger, vehicle, curve)"]
    CHARGE --> COST["Calculate complete cost<br/>energy + detour energy + booking/platform fees"]
    COST --> CHECKS["Evaluate constraints independently"]
    CHECKS --> RESERVE{"Arrival SoC ≥ reserve?"}
    CHECKS --> BUDGET{"Total cost ≤ budget?"}
    CHECKS --> DEADLINE{"ETA ≤ arrive-by?"}
    RESERVE --> OVERALL{"All hard constraints pass?"}
    BUDGET --> OVERALL
    DEADLINE --> OVERALL

    OVERALL -->|No| INFEASIBLE["Return safe explanation<br/>battery • budget • deadline shown separately"]
    OVERALL -->|Yes| PREVIEW["Return read-only preview<br/>route • stops • SoC graph • cost • ETA • evidence"]
    PREVIEW --> MODE{"How may Vidyut act?"}
    MODE -->|Recommend only| RECOMMEND["Recommend the complete plan<br/>user performs every action"]
    MODE -->|Ask before actions| ASK["Ask for explicit approval<br/>before reservation, payment, or reroute"]
    MODE -->|Full Autopilot| LIMITS{"Still inside approved limits?"}
    ASK -->|Approved| EXECUTE["Reserve compatible charger<br/>launch persisted ongoing journey"]
    ASK -->|Declined| EDIT["Keep preview editable<br/>perform no external action"]
    LIMITS -->|Yes| EXECUTE
    LIMITS -->|No| HOLD["Pause execution<br/>explain change and request a decision"]

    classDef input fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef process fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef action fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef blocked fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    class TEXT,FORM,STRATEGY input
    class PARSE,MERGE,VEHICLE,CURVE,ROAD,STATIONS,MATRIX,OPTIMIZE,CHARGE,COST,CHECKS,PREVIEW process
    class RESERVE,BUDGET,DEADLINE,OVERALL,MODE,ASK,LIMITS decision
    class RECOMMEND,EXECUTE,EDIT action
    class INFEASIBLE,HOLD blocked
```

### 3. Real-road routing and safe degradation

Routing outages reduce precision, never safety: estimated legs are labeled, constraints remain enforced, and service failure is not misreported as “no chargers exist.”

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TD
    REQUEST["Origin, destination, vehicle and constraints"] --> RESOLVE["Resolve locations<br/>known India aliases → geocoder"]
    RESOLVE --> LOCATION_OK{"Both coordinates valid?"}
    LOCATION_OK -->|No| LOCATION_FAIL["Return actionable location error<br/>do not fabricate a route"]
    LOCATION_OK -->|Yes| PRIMARY["Call primary OSRM route service"]
    PRIMARY --> PVALID{"Route has valid geometry,<br/>distance and duration?"}
    PVALID -->|Yes| PROUTE["Use measured primary road route"]
    PVALID -->|No / timeout / 5xx| REFERENCE["Call reference OSRM service"]
    REFERENCE --> RVALID{"Reference route valid?"}
    RVALID -->|Yes| RROUTE["Use measured reference road route"]
    RVALID -->|No| ESTIMATE["Use conservative labeled road estimate<br/>never present straight-line distance as measured road data"]

    PROUTE --> CORRIDOR["Build route corridor and order stations by progress"]
    RROUTE --> CORRIDOR
    ESTIMATE --> CORRIDOR
    CORRIDOR --> FILTER["Filter candidates<br/>reachable • compatible • open • safe • available"]
    FILTER --> CANDIDATES{"At least one usable candidate?"}
    CANDIDATES -->|No| NO_SEQUENCE["Return no safe charger sequence<br/>with battery/budget evidence"]
    CANDIDATES -->|Yes| TABLE["Request station-to-station OSRM table"]
    TABLE --> COMPLETE{"Every required matrix leg available?"}
    COMPLETE -->|Yes| MEASURED["Optimize with measured road legs"]
    COMPLETE -->|Partially| PARTIAL["Keep measured legs<br/>estimate only missing cells"]
    COMPLETE -->|Service unavailable| FALLBACK["Create conservative matrix estimate<br/>retain stations and mark provenance"]
    MEASURED --> CONSTRAINTS["Recheck connector, reachability,<br/>reserve, budget and deadline"]
    PARTIAL --> CONSTRAINTS
    FALLBACK --> CONSTRAINTS
    CONSTRAINTS --> FEASIBLE{"Safe sequence remains feasible?"}
    FEASIBLE -->|Yes| RESULT["Return plan with per-leg<br/>MEASURED / ESTIMATED evidence"]
    FEASIBLE -->|No| NO_SEQUENCE

    classDef request fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef success fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef fallback fill:#ffedd5,stroke:#ea580c,color:#7c2d12,stroke-width:2px
    classDef validation fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef blocked fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    class REQUEST,RESOLVE,PRIMARY,REFERENCE,TABLE request
    class PROUTE,RROUTE,MEASURED,RESULT success
    class LOCATION_OK,PVALID,RVALID,CANDIDATES,COMPLETE,FEASIBLE decision
    class ESTIMATE,PARTIAL,FALLBACK fallback
    class CORRIDOR,FILTER,CONSTRAINTS validation
    class LOCATION_FAIL,NO_SEQUENCE blocked
```

### 4. Ongoing journey and charger-failure recovery

The live trip remains on the Owner dashboard for up to 72 hours, while a charger failure triggers a mode-aware and constraint-aware recovery instead of deleting the journey.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","actorBkg":"#e0f2fe","actorBorder":"#0284c7","actorTextColor":"#0c4a6e","actorLineColor":"#94a3b8","signalColor":"#475569","signalTextColor":"#0f172a","activationBkgColor":"#ecfdf5","activationBorderColor":"#059669","labelBoxBkgColor":"#fef3c7","labelBoxBorderColor":"#d97706","labelTextColor":"#78350f","loopTextColor":"#4c1d95","loopLineColor":"#a78bfa","noteBkgColor":"#f5f3ff","noteBorderColor":"#7c3aed","noteTextColor":"#4c1d95","sequenceNumberColor":"#ffffff"}}}%%
sequenceDiagram
    actor Driver
    participant App as Owner dashboard
    participant API as Vidyut backend
    participant Booking as Booking service
    participant Ops as Host / Company operations
    participant Router as Route optimizer
    participant Notify as Notifications

    Driver->>App: Confirm feasible plan and start journey
    App->>API: Launch trip with selected vehicle and authority mode
    API->>Booking: Reserve planned compatible charging stops
    Booking-->>API: Reservation identifiers and prices
    API-->>App: Persist trip, stop timeline, ETA, cost and SoC graph
    Note over App,API: Active journey remains recoverable on the dashboard for 72 hours

    loop While journey is active
        Ops-->>API: Publish live availability, occupancy, queue and charger health
        API-->>App: Refresh progress, arrival estimate and stop status
    end

    Ops->>API: Selected charger enters FAULT or MAINTENANCE
    API->>Booking: Protect session and release unusable future reservation
    API->>Router: Find reachable connector-compatible replacement
    Router->>Router: Recheck reserve, budget, deadline, detour and charging curve
    Router-->>API: Replacement candidate + ETA/cost/SoC impact, or safe failure reason

    alt Safe replacement exists
        alt Recommend only
            API-->>App: Explain disruption and recommend replacement without taking action
        else Ask before actions
            API-->>App: Request reroute and booking approval with exact impact
            Driver->>App: Approve proposed replacement
            App->>API: Confirm reroute action
            API->>Booking: Reserve replacement charger
        else Full Autopilot and still inside limits
            API->>Booking: Reserve replacement automatically
            API-->>App: Publish automatic reroute and audit explanation
        end
        API->>Notify: Send charger-change and updated-arrival notice
        Notify-->>Driver: New stop, navigation, ETA and cost
    else No safe replacement exists
        API-->>App: Keep journey visible and show battery-safe stop guidance with the unmet constraint
        API->>Notify: Escalate assistance instead of inventing a route
    end
```

### 5. Host property, Company partnership, solar and live operations

This is the complete Host-to-station lifecycle used by the Prince Host and TATA-operated demo assets.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TD
    subgraph ONBOARDING["Host onboarding"]
        HOST["Prince creates property listing<br/>location • access • parking • opening hours"]
        EVIDENCE["Upload evidence<br/>ownership • electricity bill • photos/video • coordinates"]
        HOST --> EVIDENCE
    end

    subgraph VERIFY["Admin verification and publishing"]
        ADMIN["Review identity, ownership and site evidence"]
        HISTORY["Review Host history<br/>old-station rating • reliability • disputes"]
        METHOD["Select verification<br/>video call or physical site visit"]
        VERIFIED{"Evidence and site verified?"}
        ADMIN --> HISTORY --> METHOD --> VERIFIED
        VERIFIED -->|No| REWORK["Request missing evidence<br/>keep property unpublished"]
        REWORK --> EVIDENCE
        VERIFIED -->|Yes| PUBLISH["Publish property opportunity<br/>without transferring charger ownership"]
    end

    EVIDENCE --> ADMIN

    subgraph PARTNERSHIP["Company marketplace and agreement"]
        MARKET["Verified companies discover property"]
        PROFILE["TATA reviews Host profile<br/>location • rating • power • traffic • documents"]
        SURVEY["Request video or physical survey<br/>record survey outcome"]
        PROPOSAL["Company proposal<br/>charger mix • capex • revenue share • SLA • timeline"]
        APPROVE{"Host approves proposal?"}
        NEGOTIATE["Decline or request revised commercial terms"]
        PROJECT["Create installation project<br/>TATA owns/operates charger; Prince hosts property"]
        MARKET --> PROFILE --> SURVEY --> PROPOSAL --> APPROVE
        APPROVE -->|No| NEGOTIATE --> PROPOSAL
        APPROVE -->|Yes| PROJECT
    end

    PUBLISH --> MARKET

    subgraph LIVE_OPS["Live operation and business assistance"]
        INSTALL["Install, inspect and commission connectors"]
        LIVE["Station goes live<br/>availability and booking enabled"]
        TELEMETRY["Monitor occupied chargers and active cars<br/>energy • queue • load • fault • session value"]
        HOST_AI["Host Assistant<br/>revenue • servicing • opening-hour recommendations"]
        COMPANY_AI["Company Assistant<br/>network health • tickets • pricing • station actions"]
        DEALS["Compare equipment, service and energy-company offers"]
        SOLAR["Solar and government-support pathway<br/>eligibility • estimate • documents • financing"]
        CONSENT{"Host approval required<br/>before applications or financial actions"}
        INSTALL --> LIVE --> TELEMETRY
        TELEMETRY --> HOST_AI
        TELEMETRY --> COMPANY_AI
        HOST_AI --> DEALS
        HOST_AI --> SOLAR --> CONSENT
    end

    PROJECT --> INSTALL

    classDef host fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef governance fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef live fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef blocked fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    classDef business fill:#ecfccb,stroke:#65a30d,color:#365314,stroke-width:2px
    class HOST,EVIDENCE,PROFILE,SURVEY,PROPOSAL,NEGOTIATE host
    class ADMIN,HISTORY,METHOD,MARKET,HOST_AI,COMPANY_AI governance
    class VERIFIED,APPROVE,CONSENT decision
    class PUBLISH,PROJECT,INSTALL,LIVE,TELEMETRY live
    class REWORK blocked
    class DEALS,SOLAR business
```

### 6. Booking and charging-session lifecycle

The charger is reserved and paid through a traceable session lifecycle; faults and tamper events leave it only through inspection or maintenance.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
stateDiagram-v2
    state "Available<br/>discoverable and bookable" as Available
    state "Reserved<br/>slot + price held" as Reserved
    state "Charging<br/>occupied + metering" as Charging
    state "Completed<br/>receipt + settlement" as Completed
    state "Fault isolated<br/>new bookings disabled" as Isolated
    state "Security lock<br/>tamper response" as SecurityLock
    state "Inspection<br/>operator verification" as Inspection
    state "Maintenance<br/>ticket + repair" as Maintenance

    [*] --> Available
    Available --> Reserved: compatible booking confirmed
    Reserved --> Charging: driver authenticates and plugs in
    Reserved --> Available: cancellation or reservation timeout
    Charging --> Completed: stop request or target SoC reached
    Completed --> Available: payment and connector release complete
    Available --> Maintenance: planned service window
    Charging --> Isolated: electrical or hardware fault
    Available --> Isolated: health check fails
    Available --> SecurityLock: tamper sensor event
    Charging --> SecurityLock: cable or enclosure tamper
    SecurityLock --> Inspection: notify Company and Admin
    Inspection --> Available: evidence confirms safe operation
    Inspection --> Maintenance: repair required
    Isolated --> Maintenance: maintenance ticket assigned
    Maintenance --> Inspection: repair completed

    classDef healthy fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef active fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef caution fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef danger fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    class Available,Completed healthy
    class Reserved,Charging active
    class Inspection,Maintenance caution
    class Isolated,SecurityLock danger
```

### 7. Charger fault, tamper and customer-protection response

This separates the infrastructure repair workflow from the trip rerouting workflow while keeping both coordinated through one auditable incident.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TD
    SIGNAL["Health telemetry or tamper sensor<br/>fault code • load • temperature • cable state"] --> DETECT{"Safety or availability issue?"}
    DETECT -->|No| MONITOR["Continue live monitoring"]
    DETECT -->|Yes| INCIDENT["Create timestamped incident<br/>station • charger • evidence • severity"]
    INCIDENT --> ISOLATE["Isolate only affected charger<br/>stop new bookings; keep healthy chargers online"]
    ISOLATE --> ACTIVE{"Active session or future bookings affected?"}
    ACTIVE -->|Yes| PROTECT["Stop safely or preserve session state<br/>calculate refund and delay impact"]
    ACTIVE -->|No| TICKET["Open Company maintenance ticket"]
    PROTECT --> REROUTE["Run connector-aware replacement search<br/>reserve • budget • deadline rechecked"]
    REROUTE --> ALTERNATIVE{"Safe alternative available?"}
    ALTERNATIVE -->|Yes| MODE["Apply Owner autonomy rule<br/>recommend • ask • autopilot"]
    ALTERNATIVE -->|No| ASSIST["Keep journey visible<br/>send safe-stop and support guidance"]
    MODE --> NOTIFY["Notify affected drivers, Host, Company and Admin"]
    ASSIST --> NOTIFY
    NOTIFY --> TICKET
    TICKET --> REPAIR["Technician diagnosis<br/>repair cost • parts • SLA • expected reopening"]
    REPAIR --> VERIFY{"Inspection and telemetry healthy?"}
    VERIFY -->|No| TICKET
    VERIFY -->|Yes| RESTORE["Return charger online<br/>restore bookings and record downtime"]
    RESTORE --> AUDIT["Close incident with evidence<br/>customer impact • revenue loss • actions taken"]

    classDef input fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef process fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef danger fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    classDef governance fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    class SIGNAL input
    class MONITOR,PROTECT,REROUTE,MODE,NOTIFY,REPAIR,RESTORE process
    class DETECT,ACTIVE,ALTERNATIVE,VERIFY decision
    class INCIDENT,ISOLATE,ASSIST danger
    class TICKET,AUDIT governance
```

### 8. Least-disruptive Admin intervention

The Admin Assistant recommends the smallest effective control and leaves full identity restriction as an exceptional Super Admin action.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TD
    ISSUE["Risk, fraud, fault, dispute or compliance signal"] --> EVIDENCE["Collect evidence<br/>account history • asset state • bookings • payments • incidents"]
    EVIDENCE --> AGENT["Admin Assistant recommends<br/>least-disruptive scope, duration and reason"]
    AGENT --> SCOPE{"Smallest affected scope"}
    SCOPE --> USER["EV User capability<br/>warn • verify • restrict booking • freeze payment"]
    SCOPE --> HOST["Host asset<br/>hide property • pause listing • freeze payout • reverify site"]
    SCOPE --> COMPANY["Company capability<br/>pause publishing • bookings • marketplace • settlement"]
    SCOPE --> CHARGER["Station / charger<br/>offline • maintenance • emergency isolation"]
    SCOPE --> TRANSACTION["Booking / payment<br/>cancel • refund • hold • dispute review"]
    USER --> REVIEW{"Admin approves recommendation?"}
    HOST --> REVIEW
    COMPANY --> REVIEW
    CHARGER --> REVIEW
    TRANSACTION --> REVIEW
    REVIEW -->|No| CLOSE["Record review; apply no control"]
    REVIEW -->|Yes| APPLY["Apply scoped control<br/>preserve unrelated accounts and assets"]
    APPLY --> NOTICE["Notify affected party<br/>reason • impact • appeal • expiry"]
    NOTICE --> AUDIT["Immutable audit trail<br/>actor • before/after • evidence • timestamp"]
    AUDIT --> EXPIRY["Scheduled review or automatic expiry"]
    EXPIRY --> RESTORE["Restore capability when conditions pass"]

    AGENT --> EMERGENCY{"Severe identity fraud or security compromise?"}
    EMERGENCY -->|No| SCOPE
    EMERGENCY -->|Yes; Super Admin only| IDENTITY["Temporary identity restriction<br/>explicit reason and review deadline"]
    IDENTITY --> NOTICE

    classDef issue fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef control fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef governance fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef audit fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    class ISSUE issue
    class SCOPE,REVIEW,EMERGENCY decision
    class USER,HOST,COMPANY,CHARGER,TRANSACTION control
    class EVIDENCE,AGENT,IDENTITY governance
    class APPLY,NOTICE,AUDIT,EXPIRY,RESTORE,CLOSE audit
```

### 9. Role access and agent execution authority

Role access decides what data and assets are visible; agent mode separately decides whether a permitted action is only recommended, approved first, or automated inside limits.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","clusterBkg":"#f8fafc","clusterBorder":"#cbd5e1","edgeLabelBackground":"#ffffff"}}}%%
flowchart TB
    LOGIN["Authenticate<br/>JWT + account status"] --> ROLE{"Authorized workspace role"}
    ROLE --> OWNER["EV Owner"]
    ROLE --> HOST["Host"]
    ROLE --> COMPANY["Company"]
    ROLE --> ADMIN["Admin / Super Admin"]

    OWNER --> O_CAP["Own garage and wallet<br/>discover • plan • book • charge • view live trips"]
    HOST --> H_CAP["Own properties and host revenue<br/>publish evidence • review proposals • monitor hosted sites"]
    COMPANY --> C_CAP["Own network operations<br/>stations • chargers • pricing • tickets • partnerships"]
    ADMIN --> A_CAP["Platform governance<br/>verification • incidents • support • scoped controls • audit"]

    O_CAP --> MODE{"Assistant execution mode"}
    H_CAP --> MODE
    C_CAP --> MODE
    MODE --> RECOMMEND["Recommend only<br/>analyze and explain; human executes"]
    MODE --> ASK["Ask before actions<br/>prepare action; wait for explicit approval"]
    MODE --> AUTO["Autopilot<br/>execute low-risk actions within saved limits"]
    RECOMMEND --> AUDIT["Role-scoped response and audit context"]
    ASK --> AUDIT
    AUTO --> LIMITS{"Permission, asset ownership,<br/>risk and monetary limits still pass?"}
    LIMITS -->|Yes| AUDIT
    LIMITS -->|No| BLOCK["Block cross-boundary or over-limit action<br/>request authorized human decision"]

    A_CAP --> ADMIN_AGENT["Admin Assistant proposes governance action<br/>Admin approval remains explicit"]
    ADMIN_AGENT --> AUDIT

    classDef actor fill:#e0f2fe,stroke:#0284c7,color:#0c4a6e,stroke-width:2px
    classDef process fill:#ecfdf5,stroke:#059669,color:#064e3b,stroke-width:2px
    classDef decision fill:#fef3c7,stroke:#d97706,color:#78350f,stroke-width:2px
    classDef intelligence fill:#f5f3ff,stroke:#7c3aed,color:#4c1d95,stroke-width:2px
    classDef blocked fill:#ffe4e6,stroke:#e11d48,color:#881337,stroke-width:2px
    class LOGIN,OWNER,HOST,COMPANY,ADMIN actor
    class O_CAP,H_CAP,C_CAP,A_CAP,AUDIT process
    class ROLE,MODE,LIMITS decision
    class RECOMMEND,ASK,AUTO,ADMIN_AGENT intelligence
    class BLOCK blocked
```

### 10. Core data relationships and operational evidence

The ER view includes the fields that make route decisions, ownership boundaries, charging sessions, payments, and Admin actions explainable.

```mermaid
%%{init: {"theme":"base","themeVariables":{"background":"#ffffff","fontFamily":"Inter, ui-sans-serif, system-ui, sans-serif","fontSize":"15px","primaryColor":"#ecfdf5","primaryTextColor":"#0f172a","primaryBorderColor":"#059669","lineColor":"#475569","secondaryColor":"#e0f2fe","tertiaryColor":"#f5f3ff","attributeBackgroundColorEven":"#f8fafc","attributeBackgroundColorOdd":"#ffffff"}}}%%
erDiagram
    ACCOUNT {
        UUID id PK
        string email
        string role
        string account_status
    }
    EV_PROFILE {
        UUID id PK
        UUID account_id FK
        decimal wallet_balance
    }
    VEHICLE {
        UUID id PK
        string model
        decimal battery_kwh
        decimal efficiency_wh_km
        decimal max_dc_kw
        string connectors
    }
    HOST_PROFILE {
        UUID id PK
        UUID account_id FK
        decimal rating
        string verification_status
    }
    PROPERTY {
        UUID id PK
        UUID host_id FK
        string city
        string ownership_status
        string publication_status
    }
    COMPANY {
        UUID id PK
        UUID account_id FK
        string verification_status
        string agent_mode
    }
    INSTALLATION_PROJECT {
        UUID id PK
        UUID property_id FK
        UUID company_id FK
        string proposal_status
        decimal revenue_share
    }
    STATION {
        UUID id PK
        UUID property_id FK
        UUID operator_id FK
        string ownership_type
        string availability
    }
    CONNECTOR {
        UUID id PK
        UUID station_id FK
        string connector_type
        decimal rated_power_kw
        decimal price_per_kwh
        string status
    }
    AUTOPILOT_TRIP {
        UUID id PK
        UUID vehicle_id FK
        string autonomy_mode
        string optimization_mode
        datetime requested_arrival
        datetime expected_arrival
        boolean deadline_feasible
    }
    AUTOPILOT_STOP {
        UUID id PK
        UUID trip_id FK
        UUID station_id FK
        decimal arrival_soc
        decimal target_soc
        int charge_minutes
        string route_evidence
    }
    BOOKING {
        UUID id PK
        UUID connector_id FK
        UUID account_id FK
        string status
        datetime reserved_at
    }
    CHARGING_SESSION {
        UUID id PK
        UUID booking_id FK
        decimal energy_kwh
        decimal total_cost
        string session_status
    }
    PAYMENT {
        UUID id PK
        UUID booking_id FK
        decimal amount
        string payment_status
    }
    NOTIFICATION {
        UUID id PK
        UUID account_id FK
        string type
        string delivery_status
    }
    ADMIN_AUDIT_LOG {
        UUID id PK
        UUID admin_account_id FK
        string target_scope
        string action
        string reason
        datetime created_at
    }

    ACCOUNT ||--o| EV_PROFILE : owns
    ACCOUNT ||--o| HOST_PROFILE : owns
    ACCOUNT ||--o| COMPANY : administers
    EV_PROFILE ||--o{ VEHICLE : registers
    HOST_PROFILE ||--o{ PROPERTY : lists
    PROPERTY ||--o{ INSTALLATION_PROJECT : receives
    COMPANY ||--o{ INSTALLATION_PROJECT : proposes
    COMPANY ||--o{ STATION : operates
    PROPERTY ||--o{ STATION : hosts
    STATION ||--o{ CONNECTOR : contains
    VEHICLE ||--o{ AUTOPILOT_TRIP : uses
    AUTOPILOT_TRIP ||--o{ AUTOPILOT_STOP : plans
    STATION ||--o{ AUTOPILOT_STOP : selected_for
    ACCOUNT ||--o{ BOOKING : creates
    CONNECTOR ||--o{ BOOKING : accepts
    BOOKING ||--o| CHARGING_SESSION : starts
    BOOKING ||--o{ PAYMENT : charges
    ACCOUNT ||--o{ NOTIFICATION : receives
    ACCOUNT ||--o{ ADMIN_AUDIT_LOG : records
```

## Implemented workspaces

### EV Owner

- Connector-aware garage, including maximum AC/DC power, efficiency, charging efficiency, and supported connectors.
- Autopilot text intent parsing plus structured origin, destination, SoC, reserve, budget, deadline, trip purpose, autonomy, and optimization controls.
- Vehicle comparison that explains which owned EV is better for the selected corridor.
- Road-route preview, charging-stop selection, bookings, wallet, AutoPay, live charging, current-journey analytics, notifications, and rerouting.
- Persistent current-trip recovery: the backend exposes the latest active journey for up to `VIDYUT_AUTOPILOT_CURRENT_TRIP_MAX_AGE_HOURS` (72 hours by default).

Autonomy and optimization are independent:

| Control | Meaning |
| --- | --- |
| Recommend only | Plan and explain; never book, pay, cancel, or reroute |
| Ask before actions | Plan automatically; require confirmation before execution |
| Full Autopilot | Execute allowed operations inside the selected limits |
| Fastest | Minimize total trip time, including drive, detour, queue, setup, and charging time |
| Balanced | Balance time, cost, detour, queue, charger speed, safety, and reliability |
| Lowest cost | Minimize charging expense while preserving all hard constraints |

### Host

- Property listing and evidence workflow, email/KYC/bank verification, availability, bookings, payouts, reviews, reports, and notifications.
- Live connector occupancy derived from charging-session state rather than an AI guess.
- Host Assistant for revenue, operating hours, service priority, repair impact, alternate charging locations, company comparisons, and approval-controlled actions.
- Separate Offers & Green Finance workspace for operator comparisons, purchase/finance/RESCO solar models, and clearly labeled unverified assistance leads.
- Prince demo portfolio across Lucknow, Kanpur, Jhansi, Bhopal, and Agra, including multi-operator ownership, TATA demo chargers, a fault-ready connector, live sessions, and a solar property.

### Company

- Stations, chargers, monitoring, bookings, sessions, maintenance, pricing, analytics, revenue, settlements, staff, reports, and notifications.
- Verified Host-property discovery, complete Host/property review, saved properties, product catalogue, survey/proposal pipeline, and partnership projects.
- One Company Assistant for operational questions and permission-controlled actions.
- A separate Expansion Intelligence dashboard for read-only site ranking using grid capacity, parking readiness, and existing-network gaps.

### Admin control plane

- Separate Admin authentication and capability-scoped workspaces.
- Company, Host, property, station, charger-product, and institutional-access verification.
- Network operations, live sessions, incidents, maintenance, settlements, support, announcements, green-assistance records, AI network memory, and immutable audit history.
- Least-disruptive operational controls: restrict a capability or asset before considering emergency identity suspension.
- Independently scrollable Admin navigation so all destinations remain reachable on short screens.

## Detailed product behavior

### Journey request model

The Owner can describe a journey conversationally, fill the structured controls, or combine both. Explicitly edited fields remain visible so the driver can verify what the NLP parser understood before asking Vidyut to build a plan.

| Field | Purpose | Constraint behavior |
| --- | --- | --- |
| Vehicle | Selects battery, efficiency, connector support, and charging limits | Only compatible stations are considered |
| Origin and destination | Defines the road corridor | Resolved through the configured geocoder |
| Current battery | Energy available at departure | Must be sufficient to reach the first safe stop |
| Safety reserve | Minimum allowed SoC | Hard constraint at stops and destination |
| Maximum budget | Maximum charging spend | Hard constraint in every strategy |
| Arrive by | Requested destination clock time | Hard feasibility check when supplied |
| Trip purpose | Flexible, mall, rest/food, commute, or destination charging | Influences stop suitability and explanation |
| Autonomy | Recommend, ask first, or Full Autopilot | Controls permission to mutate state |
| Optimization | Fastest, balanced, or lowest cost | Controls how feasible candidates are ranked |

The text parser supports examples such as:

```text
Take my Tata Nexon EV from Kanpur to Bhopal.
Start at 50%, keep 10% reserve, stay under ₹1,500,
arrive by 18:30, ask before actions, and minimize total trip time.
```

The parser does not silently grant action authority. A phrase that changes the route objective cannot convert Recommend-only into Full Autopilot unless the autonomy intent is explicit and the user can see the resulting selection.

### Preview output

The read-only preview returns enough evidence to evaluate the proposal before execution:

- selected vehicle and connector compatibility;
- road distance, driving minutes, total journey minutes, and route geometry;
- route provenance: primary OSRM, reference OSRM, or conservative estimate;
- compatible chargers evaluated and selected charging sequence;
- arrival/departure SoC for every stop;
- energy added, charger rating, effective battery-side power, queue, setup, and charging minutes;
- charging cost by stop and total budget remaining;
- estimated arrival, requested arrival, deadline feasibility, and minutes late;
- independent reserve, budget, and deadline statuses plus `overallFeasible`;
- an explanation that distinguishes measured values from degraded estimates.

Recommend-only can retain this proposal without creating reservations or payment holds. Ask-before-actions displays the exact mutations awaiting approval. Full Autopilot displays the limits that bound automatic behavior.

### Vehicle recommendation

“Choose my best car” evaluates every vehicle in the signed-in garage against the same origin, destination, starting SoC, reserve, budget, deadline, and optimization strategy. The comparison includes:

- connector coverage on the corridor;
- reachable first charger and safe destination arrival;
- required number of stops;
- charging and driving time;
- expected charging spend;
- deadline feasibility;
- an explanation of why the recommended car outranks alternatives.

Priyanshu’s demo garage deliberately contains both realistic CCS2 vehicles and clearly labeled CHAdeMO, GB/T, Type 2, and Type 1 compatibility-test vehicles. The test vehicles demonstrate rejection and poor-coverage cases without assigning false connector support to a real Tata or Mahindra model.

### Ongoing journey dashboard

After confirmation, the journey remains the primary Owner view while it is active. Refreshing the application reloads the current trip rather than dropping the driver back into a blank planner. The dashboard can display:

| Area | Live information |
| --- | --- |
| Progress | current leg, completed stops, remaining distance, and ETA |
| Battery | live/current SoC, planned arrival SoC, reserve threshold, and target charge |
| Charging | active connector, effective power, energy, remaining minutes, and AutoPay state |
| Route | road polyline, current stop, next stop, compatible alternatives, and reroute state |
| Cost | held/paid charging cost, projected total, and remaining budget |
| Events | booking, departure, queue, charging, warning, fault, reroute, and completion timeline |

A charger failure is not merely a front-end animation. The backend identifies the failed connector, releases or changes affected booking state, finds a reachable compatible alternative, recalculates the remaining road route, and applies the correct autonomy rule before publishing the updated timeline.

### Lowest-cost accounting

Lowest-cost mode ranks the total journey impact rather than blindly choosing the smallest advertised ₹/kWh value:

```text
total charging journey cost
  = purchased charging energy
  + detour energy cost
  + booking and platform fees, when present
```

Extra stops can therefore lose even when their energy tariff is cheaper. Reserve, connector compatibility, reachability, station availability, maximum budget, and a hard arrival deadline remain constraints rather than optional scoring weights.

### Host operational workflow

| Host area | Implemented behavior |
| --- | --- |
| Properties | Create/edit sites, attach ownership/electricity/video evidence, track verification and publishing state |
| Companies | Compare verified operators and charger products without exposing protected contact too early |
| Installations | Request equipment, review proposals, accept/decline, and follow project milestones |
| Chargers | Show property owner separately from equipment operator; manage availability and connector state |
| Monitoring | Read occupied/available/reserved/fault state from backend sessions and connectors |
| Repair impact | Estimate affected customers, compatible alternatives, downtime, repair cost, and revenue at risk |
| Bookings | Confirm, cancel, reschedule, and track upcoming demand |
| Earnings | Daily/weekly/monthly summaries, transaction history, tax display, and verified-bank payout request |
| Reviews | Reputation score, replies, and abuse reporting |
| Assistant | Answer using the Host’s own bookings, sessions, chargers, revenue, offers, and maintenance data |
| Green finance | Compare purchase, finance, and RESCO/PPA structures with explicit eligibility disclaimers |

The Host Assistant prepares rather than fabricates external outcomes. Actions such as contacting a company, changing a charger state, opening a service request, or preparing a finance checklist are represented as approval-controlled operations with a recorded data basis.

### Company operational workflow

| Company area | Implemented behavior |
| --- | --- |
| Network | Company-owned/operated stations and connectors only |
| Live monitoring | Availability, occupied sessions, faults, load, queues, and session status |
| Maintenance | Create/update tickets and isolate a faulty connector without disabling unrelated stations |
| Property marketplace | Inspect verified property facts and the permitted Host profile before survey decisions |
| Catalogue | Submit charger products and compliance evidence for Admin approval |
| Partnerships | Track interest, survey, proposal, Host acceptance, installation, and live-station creation |
| Pricing | Update permitted station pricing and model bounded automatic changes |
| Revenue | Company revenue, payouts, settlements, and reports |
| Expansion Intelligence | Rank verified candidate sites from measurable growth evidence |
| Company Assistant | Explain or prepare operational actions within one company’s authority settings |

Company Assistant and Expansion Intelligence intentionally look and behave differently. The assistant is conversational and can have execution permission; Expansion Intelligence is a decision dashboard and cannot independently mutate the network.

### Admin governance workflow

Admin work is capability-scoped. A Verification Admin does not automatically gain settlement or Super Admin authority, and the separate Admin login cannot be reached with a normal EV/Host/Company token.

| Admin area | Typical scope |
| --- | --- |
| Command center | platform metrics and review queues |
| Accounts & staff | warnings, capability restrictions, staff roles, and exceptional identity access |
| Trust & verification | companies, representatives, banks, charger evidence, Hosts, properties, site video, and inspections |
| Network operations | stations, connectors, live sessions, incidents, maintenance, and emergency isolation |
| Marketplace control | property publishing, charger-product review, and installation oversight |
| Finance & settlements | payouts, refunds, settlement holds, and green-assistance records |
| Revenue intelligence | platform and operator trends |
| Support & notices | support cases and announcements |
| AI network control | trip outcomes, reroute performance, and route-experience memory |
| Audit trail | actor, action, resource, before/after value, reason, and timestamp |

The UI favors asset or capability intervention. For example, a cooling fault on `TATA-KNP-03` should put that connector into maintenance, notify the operator, create a ticket, and reroute affected drivers while the rest of the company network remains available.

## API surface map

The following table highlights the route groups used by the current clients. It is not a substitute for generated OpenAPI documentation.

| Route family | Main responsibilities |
| --- | --- |
| `/api/auth/**` | login, Google authentication, registration, mode switching, Host application, and profile completion |
| `/api/ev/autopilot/**` | NLP intent, vehicle ranking, previews, current trip, launch, fault recovery, reroute approval, charging completion, and experience memory |
| `/api/ev/agent/chat` | authenticated bridge to the Python agent |
| `/api/ev/vehicles/**` | garage and vehicle charging/connector profiles |
| `/api/ev/bookings/**` | Owner booking lifecycle |
| `/api/ev/payments/**` and wallet routes | payment history, holds, AutoPay, and demo top-up |
| `/api/routing/**` | direct route plans, alternatives, diversion, and status |
| `/api/host/**` | Host profile, verification, monitoring, charger state, bookings, earnings, reviews, reports, notifications, and assistant actions |
| `/api/host/marketplace/**` | company discovery, installation requests, proposals, and company interests |
| `/api/company/**` | company network, monitoring, maintenance, pricing, bookings, staff, assistant, reports, and settlements |
| `/api/company/marketplace/**` | products, verified Host opportunities, saved sites, interests, surveys, proposals, and project status |
| `/api/admin/auth/**` | isolated administrator authentication |
| `/api/admin/portal/**` | snapshot, verification workflows, scoped controls, incidents, finance, support, staff, AI query, and audit |

## Source directory guide

```text
ev_charger_app/
├── README.md
├── package.json                     # concurrent local launcher
├── vidyut-backend/
│   ├── pom.xml
│   ├── src/main/java/com/vidyut/
│   │   ├── admin/                   # Admin control plane and audit
│   │   ├── agent/                   # backend-to-agent gateway
│   │   ├── autopilot/               # intent, planning, charging and trip state
│   │   ├── auth/                    # account login and mode switching
│   │   ├── booking/ and session/    # reservations and live charging
│   │   ├── company/ and host/       # operator workspaces
│   │   ├── land/ and marketplace/   # property-company collaboration
│   │   ├── routing/                 # OSRM, geocoding and fallbacks
│   │   ├── station/ and vehicle/    # infrastructure and garage models
│   │   └── wallet/ and payment/     # financial state
│   └── src/main/resources/demo/     # synthetic corridor and district fixtures
├── vidyut-web/
│   ├── public/vidyut-logo.svg       # canonical browser SVG mark
│   └── src/components/              # Owner, Host, Company and Admin UI
├── vidyut-ai/agent/
│   ├── vidyut_agent/                # ADK service, tools and provider fallback
│   └── tests/
└── vidyut-mobile/
    ├── app/                          # Expo Router screens by authority
    └── src/features/                 # API, BLE, offline and domain modules
```

## Road routing and safe degradation

OSRM provides road geometry, duration, and the station matrix. A second OSRM URL can be configured for wider reference coverage. The selected route engine owns the complete plan: Vidyut calculates the base road polyline, filters compatible chargers near it, optimizes SoC states, and routes through only the selected stops.

Planning does not fail closed merely because a routing dependency is temporarily unavailable:

- If both road engines fail, preview uses a labeled conservative estimate: `1.30 ×` great-circle distance at `50 km/h`.
- If only station-matrix legs fail, fallback estimates are applied only to missing charger legs.
- Estimated plans retain battery, reserve, budget, connector, and deadline checks and instruct the driver to confirm live navigation before departure.

Place names are resolved through a configurable geocoder. Development defaults to public Nominatim with an in-memory cache and one-request-per-second limiter. Public Nominatim and the public OSRM demo server are not production dependencies.

## Feasibility and charging model

Overall feasibility is the conjunction of three independent checks:

```text
overall feasible = reserve feasible
                && budget feasible
                && deadline feasible
```

The API reports requested arrival, estimated arrival, available minutes, and minutes late. A battery-safe and affordable route is therefore not labeled fully feasible when it misses a requested arrival deadline.

Charging time is calculated by SoC segment, not by dividing required energy by the charger nameplate rating:

```text
battery-side power = min(
  charger rated power,
  vehicle maximum DC power,
  charging-curve power at current SoC
) × charging efficiency
```

The current default vehicle curve is full configured power from 0–60%, 80% power from 60–80%, and 40% power from 80–100%. Each vehicle can supply its own maximum power and charging efficiency.

## Demo data

Development enables idempotent synthetic seed data:

- 112 manually curated road-corridor hubs.
- 777 district hubs across 36 Indian states and union territories.
- Five station connector standards: CCS2, Type 2, CHAdeMO, GB/T, and Type 1.
- Priyanshu Sharma demo garage with Tata Nexon EV, Tata Tigor EV, Mahindra BE 6, Mahindra XEV 9e, and connector edge-case vehicles.
- Prince Host portfolio, active charging sessions, marketplace offers, repair scenarios, and solar finance examples.

See [`vidyut-backend/src/main/resources/demo/README.md`](./vidyut-backend/src/main/resources/demo/README.md) for provenance and limitations.

## Prerequisites

- Java 17 and Maven 3.8+
- Node.js 18+ and npm 9+
- Python 3.10+
- PostgreSQL 15+
- An OSRM service or an existing Docker container named `vidyut-osrm` for the root launcher

## Start the system

### Concurrent development launch

The root command starts the existing `vidyut-osrm` container and then runs the backend, web client, and AI agent:

```powershell
npm install
npm run dev
```

### Manual launch

Backend:

```powershell
cd vidyut-backend
$env:SPRING_DATASOURCE_PASSWORD = "your_postgres_password"
mvn spring-boot:run
```

AI agent:

```powershell
cd vidyut-ai\agent
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:GOOGLE_API_KEY = "your_api_key"
python -m vidyut_agent
```

Web:

```powershell
cd vidyut-web
npm install
npm run dev
```

Mobile:

```powershell
cd vidyut-mobile
npm install
npm run android
```

Default local addresses:

| Service | Address |
| --- | --- |
| Backend API | `http://localhost:8080/api` |
| Web client | `http://localhost:5173` |
| Agent | `http://127.0.0.1:8001` |
| Local OSRM | `http://localhost:5000` |

Never commit API keys, OAuth secrets, production JWT secrets, database passwords, or real identity/ownership documents. Use the supplied `.env.example` files and environment variables.

## Verification

```powershell
# Backend: 19 focused test source files plus the Spring context suite
cd vidyut-backend
mvn test

# AI agent: tool, provider-fallback, and service-fallback tests
cd ..\vidyut-ai\agent
.\.venv\Scripts\python -m unittest discover -s tests -v

# Web: lint, TypeScript, and production bundle
cd ..\..\vidyut-web
npm run lint
npm run build

# Mobile: TypeScript and resolved Expo configuration
cd ..\vidyut-mobile
npm run typecheck
npx expo config --type public
```

## Branding

The canonical web mark is [`vidyut-web/public/vidyut-logo.svg`](./vidyut-web/public/vidyut-logo.svg). It is reused for the browser favicon, splash/login/register surfaces, the main sidebar, and the Admin Portal.

## License

See [`vidyut-mobile/LICENSE`](./vidyut-mobile/LICENSE).
