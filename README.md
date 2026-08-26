<div align="center">

# ⚡ Vidyut EV Charging & Autopilot Platform
### Enterprise-Grade Next-Gen EV Ecosystem for India • Connector-Aware Routing • Multi-Agent Autonomy • Live Grid Telemetry

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.7-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19.0_TypeScript_6-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev)
[![Vite](https://img.shields.io/badge/Vite-8.0_Fast_Bundler-646CFF?style=for-the-badge&logo=vite&logoColor=white)](https://vitejs.dev)
[![Python ADK](https://img.shields.io/badge/Python_ADK-Google_Gemini-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://ai.google.dev)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15_Flyway-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Expo](https://img.shields.io/badge/Expo_57-React_Native_0.86-000020?style=for-the-badge&logo=expo&logoColor=white)](https://expo.dev)
[![OSRM](https://img.shields.io/badge/OSRM-Real_Road_Engine-FF6B6B?style=for-the-badge&logo=openstreetmap&logoColor=white)](http://project-osrm.org)
[![License](https://img.shields.io/badge/License-Proprietary_MIT_Dual-8B5CF6?style=for-the-badge)](./vidyut-mobile/LICENSE)

<p align="center">
  <b>Vidyut</b> is a mission-critical EV charging and Autopilot route intelligence platform engineered for Indian highways and metropolitan clusters. Built with strict role boundaries, high-precision SoC charging-curve integration, real-road OSRM matrix routing, automated self-healing trip recovery, solar RESCO partnership workflows, and multi-tier AI execution authority.
</p>

---

</div>

> [!IMPORTANT]
> **Synthetic Demo Data Disclaimer**: District hubs (777), corridor hubs (112), charging networks, vehicle battery models, tariffs, and government scheme estimates included in the seeders are synthetic and designed for operational testing. They demonstrate production-grade workflows and are not real-world commercial station guarantees.

---

## 📑 Table of Contents

- [⚡ System Overview & Architecture](#-system-overview--architecture)
- [🤖 Three-Agent Multi-Tier AI Architecture](#-three-agent-multi-tier-ai-architecture)
- [🧩 Core Subsystems & Repository Structure](#-core-subsystems--repository-structure)
- [📊 Deep-Dive Architectural & Protocol Flowcharts](#-deep-dive-architectural--protocol-flowcharts)
  - [1. Enterprise Architecture & System Boundaries](#1-enterprise-architecture--system-boundaries)
  - [2. Natural-Language Autopilot & Multi-Tier Autonomy](#2-natural-language-autopilot--multi-tier-autonomy)
  - [3. Dynamic Road Intelligence & Resilient Fallback Engine](#3-dynamic-road-intelligence--resilient-fallback-engine)
  - [4. Live Ongoing Journey & Self-Healing Charger Recovery Protocol](#4-live-ongoing-journey--self-healing-charger-recovery-protocol)
  - [5. Host-to-Company Marketplace & Solar RESCO Lifecycle](#5-host-to-company-marketplace--solar-resco-lifecycle)
  - [6. High-Precision Charging Session State Machine](#6-high-precision-charging-session-state-machine)
  - [7. Charger Fault, Tamper & Grid Telemetry Architecture](#7-charger-fault-tamper--grid-telemetry-architecture)
  - [8. Principle of Least Privilege Admin Governance Engine](#8-principle-of-least-privilege-admin-governance-engine)
  - [9. RBAC & AI Execution Authority Boundaries](#9-rbac--ai-execution-authority-boundaries)
  - [10. Relational Entity-Relationship Model & Telemetry Schema](#10-relational-entity-relationship-model--telemetry-schema)
- [📡 Binary Protocols, Telemetry & Frame Schemas](#-binary-protocols-telemetry--frame-schemas)
- [🎛️ Implemented Workspaces & Cockpits](#️-implemented-workspaces--cockpits)
- [🚀 Quickstart & Concurrent Launch](#-quickstart--concurrent-launch)
- [🧪 Comprehensive Verification & Test Suite](#-comprehensive-verification--test-suite)
- [🗺️ API Surface Reference](#️-api-surface-reference)

---

## ⚡ System Overview & Architecture

Vidyut orchestrates four distinct stakeholders through dedicated role-scoped cockpits powered by a unified event-driven backend:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 ROLE-SCOPED COCKPITS                                    │
│   ┌────────────────┐   ┌─────────────────┐   ┌────────────────┐   ┌────────────────┐   │
│   │ 🚗 EV Owner    │   │ 🏢 Property Host│   │ ⚡ Charge Co.  │   │ 🛡️ Super Admin │   │
│   │  Web & Mobile  │   │  Web & Mobile   │   │  Web & Mobile  │   │  Admin Portal  │   │
│   └───────┬────────┘   └────────┬────────┘   └───────┬────────┘   └───────┬────────┘   │
└───────────┼─────────────────────┼────────────────────┼────────────────────┼────────────┘
            │                     │ mode-scoped JWT    │                    │
            ▼                     ▼                    ▼                    ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        SPRING BOOT 3.3.7 DOMAIN API GATEWAY                            │
│  ├── 🔐 Identity & Auth (RBAC / JWT / Account State)                                   │
│  ├── 🧠 Autopilot Engine (NLP Intent / SoC Curves / Multi-Stop Matrix Optimization)   │
│  ├── ⚡ Live Charging & Reservations (OCPP 1.6J/2.0.1 bridge / Telemetry / Refunds)    │
│  ├── 🤝 Marketplace & RESCO (Property Proposals / Surveys / Capex & Payout Models)    │
│  └── 🛡️ Governance & Auditing (Least-Disruptive Scoped Controls / Immutable Logs)       │
└───────────┬──────────────────────────────────────────┬────────────────────┬────────────┘
            │                                          │                    │
            ▼                                          ▼                    ▼
┌───────────────────────┐                  ┌──────────────────────┐ ┌────────────────────┐
│   POSTGRESQL 15 DB    │                  │  OSRM ROUTE ENGINE   │ │ PYTHON ADK AGENT   │
│  • Accounts & Garage  │                  │  • Primary OSRM      │ │  • Gemini 1.5 Pro  │
│  • Stations & Sockets │                  │  • Reference Mirror  │ │  • OpenRouter      │
│  • Active Sessions    │                  │  • 1.3x Geo Fallback │ │  • Scoped Tools    │
└───────────────────────┘                  └──────────────────────┘ └────────────────────┘
```

---

## 🤖 Three-Agent Multi-Tier AI Architecture

Vidyut employs a **tri-agent decoupled architecture** where distinct specialized AI agents operate with domain-scoped context, cryptographic security boundaries, and graduated action policies:

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "clusterBkg": "#0f172a90",
    "clusterBorder": "#334155",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    subgraph AGENT_1["🚗 Agent 1: EV Driver Autopilot Agent (Python ADK + Gemini)"]
        A1_NLP["🗣️ Natural-Language Parser<br/>Extracts destination, SoC, budget & deadline"]
        A1_SOLVE["🔋 Multi-Stop SoC Solver<br/>Non-linear battery curve integration & detour calculation"]
        A1_HEAL["🔄 Self-Healing Dispatcher<br/>Detects charger outages & reroutes active journey"]
        A1_TOOLS["🛠️ Scoped Tools:<br/>preview_trip, book_charger, reroute, top_up_wallet"]
        A1_NLP --> A1_SOLVE --> A1_HEAL --> A1_TOOLS
    end

    subgraph AGENT_2["🏢 Agent 2: Property Host Agent (Gemini → OpenRouter → Spring fallback)"]
        A2_OCC["📊 Occupancy & Peak Analyzer<br/>Analyzes session history to predict demand surges"]
        A2_PRICE["💰 Dynamic Pricing Advisor<br/>Recommends time-of-day tariffs within statutory bounds"]
        A2_SOLAR["☀️ Solar RESCO & Subsidy Engine<br/>Matches property load with PM Surya Ghar & PPA schemes"]
        A2_TOOLS["🔒 Read-Only Model Boundary<br/>Spring executes separately after Host approval"]
        A2_OCC --> A2_PRICE --> A2_SOLAR --> A2_TOOLS
    end

    subgraph AGENT_3["⚡ Agent 3: CPO Company Agent (Gemini → OpenRouter → Spring fallback)"]
        A3_FAULT["🚨 Grounded Fault Triage<br/>Explains impact and proposes scoped recovery"]
        A3_GROWTH["📈 Expansion Intelligence<br/>Ranks unserved corridor gaps based on grid traffic"]
        A3_AUDIT["🛡️ Company-Scoped Decisions<br/>Uses only the authenticated operator’s network data"]
        A3_TOOLS["🔒 Read-Only Model Boundary<br/>Spring executes separately under Company policy"]
        A3_FAULT --> A3_GROWTH --> A3_AUDIT --> A3_TOOLS
    end

    subgraph AUTONOMY_GOVERNANCE["🛡️ Multi-Tier Execution Guardrails"]
        G_REC["Tier 1: Recommend Only (Read-Only Explanations)"]
        G_ASK["Tier 2: Ask Before Actions (Explicit Human Confirmation)"]
        G_AUTO["Tier 3: Autopilot Execution (Automated Inside Saved Limits)"]
    end

    A1_TOOLS --> AUTONOMY_GOVERNANCE
    A2_TOOLS --> AUTONOMY_GOVERNANCE
    A3_TOOLS --> AUTONOMY_GOVERNANCE

    classDef a1 fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef a2 fill:#14532d,stroke:#16a34a,color:#f0fdf4,stroke-width:2px;
    classDef a3 fill:#2e1065,stroke:#7c3aed,color:#ede9fe,stroke-width:2px;
    classDef guard fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;

    class A1_NLP,A1_SOLVE,A1_HEAL,A1_TOOLS a1;
    class A2_OCC,A2_PRICE,A2_SOLAR,A2_TOOLS a2;
    class A3_FAULT,A3_GROWTH,A3_AUDIT,A3_TOOLS a3;
    class G_REC,G_ASK,G_AUTO guard;
```

### 🔍 Tri-Agent Responsibility Matrix

| Metric / Dimension | 🚗 Agent 1: EV Autopilot Agent | 🏢 Agent 2: Host Copilot | ⚡ Agent 3: CPO & Admin Copilot |
| :--- | :--- | :--- | :--- |
| **Primary Domain** | EV Driver Route & Charging Intelligence | Property Monetization & Green Finance | CPO Fleet Operations & Platform Governance |
| **Core Engine / Stack** | Python ADK + Gemini/OpenRouter + Spring tools | Spring analytics + Python ADK Gemini/OpenRouter explanation | Spring network analytics + Python ADK Gemini/OpenRouter explanation |
| **Input Modality** | Natural-Language Prompt + Structured UI Controls | Historical Sessions, P&L Queries, Reviews | Hardware Telemetry, Tamper Alarms, Disputes |
| **Key Scoped Tools** | `preview_autopilot_trip`, `book_charger`, `reroute`, `complete_charging` | No model tools; Spring supplies occupancy, revenue, maintenance, deal and solar context | No model tools; Spring supplies faults, pricing, revenue, expansion and offer context |
| **Autonomy Enforcement** | `Recommend` \| `Ask Before Action` \| `Full Autopilot` | Separate approval-gated Host action endpoint | Company policy + separate ownership/approval-checked action endpoint |
| **Fault Resilience** | Autonomous in-flight rerouting upon socket failure | Gemini → OpenRouter → deterministic Host answer | Gemini → OpenRouter → deterministic Company answer |

---

## 🧩 Core Subsystems & Repository Structure

| Module | Core Stack | Purpose & Responsibility |
| :--- | :--- | :--- |
| [`vidyut-backend`](./vidyut-backend) | **Java 17, Spring Boot 3.3.7, PostgreSQL, Flyway, JWT** | High-throughput transactional core: RBAC, Autopilot engine, dynamic OSRM routing, OCPP session state machines, marketplace negotiations, wallets, payments, and tamper governance. |
| [`vidyut-web`](./vidyut-web) | **React 19, TypeScript 6, Vite 8, Leaflet, Tailwind/Vanilla** | Reactive web suite featuring high-performance dashboards for EV Drivers, Property Hosts, ChargePoint Operators (CPO), and Platform Admins. |
| [`vidyut-ai/agent`](./vidyut-ai/agent) | **Python 3.10+, Google GenAI ADK, FastAPI, OpenRouter** | Natural language reasoning agent equipped with authenticated backend tool hooks, safe fallback mechanics, and strict confirmation boundaries. |
| [`vidyut-mobile`](./vidyut-mobile) | **React Native 0.86, Expo 57, Expo Router** | Cross-platform iOS/Android app featuring BLE charger handshake, offline cached corridors, real-time telemetry, and biometric authentication. |

---

## 📊 Deep-Dive Architectural & Protocol Flowcharts

### 1. Enterprise Architecture & System Boundaries

This diagram visualizes role isolation, micro-domain communication, and real-time data streaming across the platform ecosystem.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "secondaryColor": "#1e1b4b",
    "secondaryTextColor": "#f8fafc",
    "secondaryBorderColor": "#8b5cf6",
    "tertiaryColor": "#064e3b",
    "tertiaryTextColor": "#f8fafc",
    "tertiaryBorderColor": "#10b981",
    "clusterBkg": "#0f172a90",
    "clusterBorder": "#334155",
    "edgeLabelBackground": "#0f172a",
    "fontFamily": "Inter, ui-sans-serif, system-ui, sans-serif",
    "fontSize": "14px"
  }
}}%%
flowchart TB
    subgraph COCKPIT["🎛️ Role-Scoped Experiences (Web & Mobile)"]
        OWNER["🚗 EV Owner Cockpit<br/>Garage • Corridor Planner • Booking • Live Journey"]
        HOST["🏢 Property Host Cockpit<br/>Listings • Occupancy • Revenue • Maintenance"]
        COMPANY["⚡ ChargePoint Operator<br/>Stations • Pricing • Telemetry • Staff Ops"]
        ADMIN["🛡️ Platform Control Plane<br/>Verification • Incident Isolation • Scoped Admin"]
    end

    subgraph API_GATEWAY["⚡ Spring Boot Domain API (Port 8080)"]
        API["REST API & Gateway<br/>Validation • JWT Claims • Rate Limiting"]
        AUTH["Identity & Access Engine<br/>Token Issuance • Multi-Role Context"]
        AUTOPILOT["Autopilot Routing Engine<br/>Intent Parsing • Multi-Stop Matrix • SoC Curves"]
        OPERATIONS["Live Charging Engine<br/>Connector Slots • Metering • AutoPay • Refunds"]
        MARKETPLACE["Property Marketplace<br/>Site Surveys • RESCO Capex • Revenue Share"]
        GOVERNANCE["Governance & Security<br/>Audit Trails • Incident Triage • Least-Privilege"]
        NOTIFY["Live Dispatcher<br/>WebSockets • Push Notifications • SMS/Email"]
    end

    subgraph INTELLIGENCE["🧠 AI Agent & Road Engine Subsystems"]
        AGENT["Python ADK Agent<br/>Read-Only Tools + Scoped Confirmation"]
        MODEL["Multi-Tier LLM Provider<br/>Gemini 1.5 Pro → OpenRouter → Fallback"]
        OSRM["Road Routing Matrix<br/>Primary OSRM → Reference OSRM"]
        GEOCODER["Spatial Resolvers<br/>Alias Dictionary → Nominatim Geocoder"]
    end

    subgraph DATA_TIER["💾 Storage & Seed Data Tier"]
        DB[("PostgreSQL 15 Cluster<br/>Trips • Wallets • Assets • Audit Logs")]
        MIGRATIONS["Flyway Migrations<br/>Versioned Schema Evolution"]
        DEMO["Demo Data Seeders<br/>112 Corridors • 777 District Hubs"]
    end

    subgraph HARDWARE["🔌 Physical Infrastructure & Edge Telemetry"]
        STATION["Smart Charging Station<br/>Hosted Site • Operator Managed"]
        CONNECTOR["High-Power Connectors<br/>CCS2 (350kW) • Type 2 • GB/T • CHAdeMO"]
        TELEMETRY["OCPP & Telemetry Engine<br/>Active kW • Temperature • Tamper Sensor"]
        SESSION["Active Charging Session<br/>Real-Time kWh • Smart Tariff • Auto-Cutoff"]
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

    classDef cockpit fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef core fill:#064e3b,stroke:#059669,color:#ecfdf5,stroke-width:2px;
    classDef ai fill:#2e1065,stroke:#7c3aed,color:#ede9fe,stroke-width:2px;
    classDef data fill:#14532d,stroke:#16a34a,color:#f0fdf4,stroke-width:2px;
    classDef edge fill:#451a03,stroke:#d97706,color:#fef3c7,stroke-width:2px;

    class OWNER,HOST,COMPANY,ADMIN cockpit;
    class API,AUTH,AUTOPILOT,OPERATIONS,MARKETPLACE,GOVERNANCE,NOTIFY core;
    class AGENT,MODEL,OSRM,GEOCODER ai;
    class DB,MIGRATIONS,DEMO data;
    class STATION,CONNECTOR,TELEMETRY,SESSION edge;
```

---

### 2. Natural-Language Autopilot & Multi-Tier Autonomy

The system merges natural-language trip parameters with hard vehicle battery constraints, evaluates multi-stop route feasibility, and routes through explicit execution authority levels.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "clusterBkg": "#0f172a90",
    "clusterBorder": "#334155",
    "edgeLabelBackground": "#0f172a",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    subgraph INPUT_STAGE["1. Intent Parsing & Context Injection"]
        TEXT["🗣️ Natural-Language Request<br/>'Take my Nexon EV to Bhopal with 10% reserve'"]
        NLP["🧠 Gemini ADK Intent Parser<br/>Extracts Origin, Destination, SoC, Budget"]
        FORM["🎛️ Explicit Parameter Controls<br/>Vehicle Selection, Reserve %, Max Budget ₹"]
        STRATEGY["⚡ Optimization Policy<br/>Fastest • Balanced • Lowest Cost"]
        TEXT --> NLP
        NLP --> MERGE["Merge & Validate Intent<br/>Explicit user overrides strictly authoritative"]
        FORM --> MERGE
    end

    subgraph CALC_STAGE["2. Battery SoC & Road Matrix Calculation"]
        VEHICLE["🔋 Vehicle Specification<br/>Capacity (kWh) • Wh/km Efficiency • DC Max kW"]
        CURVE["📉 Non-Linear Charging Curve<br/>0-60% @ 100% kW | 60-80% @ 80% kW | 80-100% @ 40% kW"]
        ROAD["🛣️ Road Route Geometry<br/>Real Distance • Elevation • Traffic Duration"]
        STATIONS["🔌 Corridor Station Filter<br/>Connector Match • Verified Status • Real-Time Queue"]
        MATRIX["📐 Distance-Time Matrix<br/>Per-Leg Detour Penalty & Arrival SoC Calculation"]
    end

    MERGE --> VEHICLE
    VEHICLE --> CURVE
    MERGE --> ROAD
    ROAD --> STATIONS
    STATIONS --> MATRIX
    CURVE --> SOLVER["🧮 Multi-Stop Sequence Optimizer<br/>Drive Time + Detour + Queue + Plug/Setup + Charge"]
    MATRIX --> SOLVER
    STRATEGY --> SOLVER

    subgraph EVAL_STAGE["3. Hard Feasibility Constraint Checks"]
        SOLVER --> INTEGRATE["Integrate Energy & Cost<br/>Effective kW = min(Charger kW, Vehicle kW, Curve kW)"]
        INTEGRATE --> C_SOC{"Arrival SoC >= Reserve% ?"}
        INTEGRATE --> C_BUDGET{"Total Cost <= Budget ₹ ?"}
        INTEGRATE --> C_TIME{"Arrival Time <= Deadline ?"}
        
        C_SOC --> ALL_PASS{"All Hard Constraints Passed?"}
        C_BUDGET --> ALL_PASS
        C_TIME --> ALL_PASS
    end

    subgraph OUTPUT_STAGE["4. Multi-Tier Autonomy Execution"]
        ALL_PASS -->|❌ NO| INFEASIBLE["⚠️ Reject Proposal & Provide Breakdown<br/>Explain exact battery deficit, cost overrun, or delay"]
        ALL_PASS -->|✅ YES| PREVIEW["📋 Return Interactive Trip Preview<br/>Leg-by-leg SoC Graph, Charger Slots, Total Cost, ETA"]
        
        PREVIEW --> AUTONOMY{"Selected Autonomy Tier"}
        AUTONOMY -->|Mode 1: Recommend Only| REC["📢 Recommend Plan Only<br/>Driver manually executes reservations"]
        AUTONOMY -->|Mode 2: Ask Before Actions| ASK["❓ Request Explicit Confirmation<br/>Displays mutation card before reserving slots"]
        AUTONOMY -->|Mode 3: Full Autopilot| BOUNDS{"Within Pre-Approved Limits?"}
        
        ASK -->|Driver Approves| EXECUTE["🚀 Book Charging Slots & Launch Active Trip"]
        ASK -->|Driver Rejects| EDIT["✏️ Return to Planner for Edits"]
        BOUNDS -->|✅ In Bounds| EXECUTE
        BOUNDS -->|❌ Out of Bounds| HOLD["⏸️ Pause & Prompt Human Driver"]
    end

    classDef stage1 fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef stage2 fill:#14532d,stroke:#16a34a,color:#f0fdf4,stroke-width:2px;
    classDef stage3 fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;
    classDef stage4 fill:#2e1065,stroke:#7c3aed,color:#ede9fe,stroke-width:2px;
    classDef blocked fill:#4c0519,stroke:#f43f5e,color:#ffe4e6,stroke-width:2px;

    class TEXT,NLP,FORM,STRATEGY,MERGE stage1;
    class VEHICLE,CURVE,ROAD,STATIONS,MATRIX,SOLVER stage2;
    class INTEGRATE,C_SOC,C_BUDGET,C_TIME,ALL_PASS stage3;
    class PREVIEW,AUTONOMY,REC,ASK,BOUNDS,EXECUTE,EDIT stage4;
    class INFEASIBLE,HOLD blocked;
```

---

### 3. Dynamic Road Intelligence & Resilient Fallback Engine

When public routing providers suffer network degradation, Vidyut automatically shifts into safe fallback modes without misrepresenting estimated telemetry as verified road measurements.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    START["📍 Origin, Destination & Vehicle Constraints"] --> GEO{"Resolve Geocodes"}
    GEO -->|Alias Lookup Hit| CACHE["⚡ Use Cached Geocoordinates"]
    GEO -->|Miss| NOMINATIM["🌐 Rate-Limited Nominatim Engine"]
    
    CACHE --> VALID_GEO{"Valid Coordinates Found?"}
    NOMINATIM --> VALID_GEO
    
    VALID_GEO -->|No| ERR_GEO["❌ Return Actionable Geocoding Error<br/>Never invent hypothetical coordinates"]
    VALID_GEO -->|Yes| P_OSRM["🛣️ Query Primary OSRM Routing Engine"]
    
    P_OSRM --> P_CHECK{"Valid Geometry & Duration?"}
    P_CHECK -->|✅ 200 OK| ROUTE_P["Use Primary Measured Road Polyline"]
    P_CHECK -->|❌ Timeout / 5xx| R_OSRM["🔄 Query Reference Mirror OSRM"]
    
    R_OSRM --> R_CHECK{"Reference Route Valid?"}
    R_CHECK -->|✅ 200 OK| ROUTE_R["Use Reference Measured Road Polyline"]
    R_CHECK -->|❌ Fail| ROUTE_EST["⚠️ Conservative Haversine Road Estimator<br/>Distance: 1.30x Great Circle • Speed: 50 km/h Labeled"]
    
    ROUTE_P --> CORRIDOR["Build Route Buffer & Filter Candidate Hubs"]
    ROUTE_R --> CORRIDOR
    ROUTE_EST --> CORRIDOR
    
    CORRIDOR --> MATRIX_REQ["Query OSRM Station Matrix Table"]
    MATRIX_REQ --> M_CHECK{"All Matrix Legs Measured?"}
    
    M_CHECK -->|Full Matrix OK| ACCURATE["Compute High-Precision Detour Optimizer"]
    M_CHECK -->|Partial Matrix| PARTIAL["Impute Missing Matrix Cells with 1.35x Multiplier"]
    M_CHECK -->|Matrix Service Down| FULL_EST["Estimate Detours with Spatial Haversine"]
    
    ACCURATE --> FINAL_CHECK{"Feasibility Hard Bounds Pass?"}
    PARTIAL --> FINAL_CHECK
    FULL_EST --> FINAL_CHECK
    
    FINAL_CHECK -->|Passed| DELIVER["🚀 Return Verified Plan with Provenance Tags<br/>(PROVENANCE: PRIMARY_MEASURED | REFERENCE | ESTIMATED)"]
    FINAL_CHECK -->|Failed| NO_FEAS["⚠️ Return Infeasible Notification with Audit Proof"]

    classDef primary fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef fallback fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;
    classDef success fill:#064e3b,stroke:#059669,color:#ecfdf5,stroke-width:2px;
    classDef danger fill:#4c0519,stroke:#f43f5e,color:#ffe4e6,stroke-width:2px;

    class START,GEO,CACHE,NOMINATIM,VALID_GEO,P_OSRM,P_CHECK,CORRIDOR,MATRIX_REQ,M_CHECK primary;
    class R_OSRM,R_CHECK,ROUTE_EST,PARTIAL,FULL_EST fallback;
    class ROUTE_P,ROUTE_R,ACCURATE,FINAL_CHECK,DELIVER success;
    class ERR_GEO,NO_FEAS danger;
```

---

### 4. Live Ongoing Journey & Self-Healing Charger Recovery Protocol

This sequence diagram depicts how a live charger outage triggers automatic rerouting and slot reservation while keeping the active journey visible on the driver's dashboard for up to 72 hours.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "actorBkg": "#0f172a",
    "actorBorder": "#38bdf8",
    "actorTextColor": "#f8fafc",
    "actorLineColor": "#64748b",
    "signalColor": "#38bdf8",
    "signalTextColor": "#f8fafc",
    "labelBoxBkgColor": "#1e293b",
    "labelBoxBorderColor": "#f59e0b",
    "labelTextColor": "#fbbf24",
    "loopTextColor": "#c084fc",
    "loopLineColor": "#a855f7",
    "noteBkgColor": "#1e1b4b",
    "noteBorderColor": "#818cf8",
    "noteTextColor": "#e0e7ff",
    "activationBkgColor": "#1e293b",
    "activationBorderColor": "#10b981"
  }
}}%%
sequenceDiagram
    autonumber
    actor Driver as 🚗 EV Driver
    participant App as 📱 Vidyut Web/Mobile App
    participant API as ⚡ Vidyut Backend API
    participant Booking as 🎟️ Booking & Lock Engine
    participant Ops as 🏢 CPO Telemetry Stream
    participant Router as 🧠 Autopilot Optimizer
    participant Notify as 🔔 Push Dispatcher

    rect rgb(15, 23, 42)
    Note over Driver,App: Phase 1: Plan Confirmation & Journey Launch
    Driver->>App: Confirm Feasible Plan & Select Autopilot Authority
    App->>API: POST /api/ev/autopilot/launch-journey
    API->>Booking: Lock & Reserve Connector Slots for Planned Stops
    Booking-->>API: Slot UUIDs, Price Locks & QR Tokens
    API-->>App: Return Active Journey Payload (72h Recovery Cache)
    end

    rect rgb(6, 78, 59)
    Note over App,Ops: Phase 2: Live Telemetry & Session Monitoring
    loop Every 10 Seconds Active Telemetry
        Ops-->>API: Broadcast Socket Health, Temperature & Real-Time Queue
        API-->>App: Push Live Progress, SoC Trend & Updated ETA
    end
    end

    rect rgb(76, 5, 25)
    Note over Ops,Notify: Phase 3: Hardware Outage & Autonomous Self-Healing
    Ops->>API: 🚨 ALERT: Reserved Charger Enters FAULT / MAINTENANCE
    API->>Booking: Release Unusable Future Reservation & Protect Session State
    API->>Router: Execute Emergency Replacement Search (SoC, Connector, Budget)
    Router->>Router: Validate Detour Constraints, Elevation & Charging Curves
    Router-->>API: Feasible Replacement Station Identified (Detour +4km, +6 min)
    end

    alt Autonomy: Full Autopilot (Within Saved Tolerances)
        API->>Booking: Automatically Lock Replacement Charger Slot
        API-->>App: Push Real-Time Reroute & Updated Route Polyline
        API->>Notify: Dispatch Immediate In-App Audio & Notification Alert
        Notify-->>Driver: 'Charger Fault: Automatically Rerouted to Station B (+6 min)'
    else Autonomy: Ask Before Actions
        API-->>App: Emit Critical Action Prompt (Show Time & Cost Delta)
        Driver->>App: Tap 'Approve Reroute to Station B'
        App->>API: POST /api/ev/autopilot/confirm-reroute
        API->>Booking: Finalize Slot Lock on Replacement Station
    else Autonomy: Recommend Only
        API-->>App: Display Advisory Alert & Show Alternative Stations on Map
    end
```

---

### 5. Host-to-Company Marketplace & Solar RESCO Lifecycle

The end-to-end commercial lifecycle for property owners onboarding sites, negotiating capex/opex with charging operators, and deploying clean solar infrastructure.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "clusterBkg": "#0f172a90",
    "clusterBorder": "#334155",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    subgraph S1["1. Host Property Onboarding"]
        H_LIST["🏢 Host Creates Property Listing<br/>Location, Parking Bays, Electrical Sanction Load"]
        H_DOCS["📄 Upload Ownership & Grid Proof<br/>Property Deed, Electricity Bill, Geotagged Photos"]
        H_LIST --> H_DOCS
    end

    subgraph S2["2. Admin Verification & Publishing"]
        ADM_REV["🛡️ Admin Compliance Audit<br/>Verify Title, Grid Capacity, Physical Video Survey"]
        ADM_DEC{"Site Meets Safety Criteria?"}
        ADM_DOCS_REQ["Request Clarifications / Site Rectification"]
        ADM_PUB["🚀 Publish Property to Operator Marketplace<br/>(Ownership Remains with Host)"]
        
        H_DOCS --> ADM_REV --> ADM_DEC
        ADM_DEC -->|❌ No| ADM_DOCS_REQ --> H_DOCS
        ADM_DEC -->|✅ Yes| ADM_PUB
    end

    subgraph S3["3. CPO Discovery & Commercial Agreement"]
        CPO_DISC["⚡ CPO Discovers Published Opportunity"]
        CPO_SURVEY["📐 Request Physical / Video Feasibility Survey"]
        CPO_PROP["💼 Submit Joint Venture Proposal<br/>Charger Mix (CCS2/AC), Capex Split, Revenue Share %"]
        H_REVIEW{"Host Commercial Review"}
        H_NEG["Negotiate Terms / Revision Request"]
        PROJ_CREATE["🤝 Execute Digital Agreement & Create Project<br/>(Operator Owns Asset • Host Owns Land)"]
        
        ADM_PUB --> CPO_DISC --> CPO_SURVEY --> CPO_PROP --> H_REVIEW
        H_REVIEW -->|❌ Counter Offer| H_NEG --> CPO_PROP
        H_REVIEW -->|✅ Accepted| PROJ_CREATE
    end

    subgraph S4["4. Commissioning, Live Operations & Solar Expansion"]
        DEPLOY["🔧 Civil Works, Grid Interconnect & Charger Commissioning"]
        GO_LIVE["🟢 Station Goes Live on Vidyut Network"]
        MONITOR["📊 Live Telemetry, Revenue Split & Dynamic Pricing"]
        SOLAR_EVAL["☀️ Solar RESCO Feasibility Engine<br/>Rooftop Solar + Battery Energy Storage (BESS) Assessment"]
        SOLAR_PROP["🌱 Apply for Green Energy Open Access & Subsidies"]
        
        PROJ_CREATE --> DEPLOY --> GO_LIVE --> MONITOR
        MONITOR --> SOLAR_EVAL --> SOLAR_PROP
    end

    classDef host fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef admin fill:#2e1065,stroke:#7c3aed,color:#ede9fe,stroke-width:2px;
    classDef cpo fill:#14532d,stroke:#16a34a,color:#f0fdf4,stroke-width:2px;
    classDef ops fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;

    class H_LIST,H_DOCS,H_REVIEW,H_NEG host;
    class ADM_REV,ADM_DEC,ADM_DOCS_REQ,ADM_PUB admin;
    class CPO_DISC,CPO_SURVEY,CPO_PROP,PROJ_CREATE cpo;
    class DEPLOY,GO_LIVE,MONITOR,SOLAR_EVAL,SOLAR_PROP ops;
```

---

### 6. High-Precision Charging Session State Machine

Every charging socket transitions through deterministic operational states to prevent double-booking, manage smart load balancing, and guarantee financial settlement.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
stateDiagram-v2
    [*] --> Available: Socket Online & Health Verified

    state "🟢 AVAILABLE\nDiscoverable in Autopilot" as Available
    state "🟡 RESERVED\nSlot Held • Token Issued" as Reserved
    state "⚡ CHARGING\nLive Metering • AutoPay Active" as Charging
    state "🔵 COMPLETED\nInvoice Generated • Settled" as Completed
    state "🔴 FAULT ISOLATED\nElectrical/Thermal Anomaly" as FaultIsolated
    state "🛡️ SECURITY LOCK\nTamper / Physical Breach" as SecurityLock
    state "🔍 INSPECTION\nTechnician Video Audit" as Inspection
    state "🔧 MAINTENANCE\nHardware Repair Window" as Maintenance

    Available --> Reserved: Verified Booking Placed
    Reserved --> Charging: Gun Plugged In & Driver Authenticated
    Reserved --> Available: Booking Timeout (15m) / Cancellation
    
    Charging --> Completed: Target SoC Reached / Driver Stopped
    Completed --> Available: Cable Unplugged & Payment Settled

    Available --> Maintenance: Scheduled Preventative Service
    Charging --> FaultIsolated: Ground Fault / Overcurrent Detected
    Available --> FaultIsolated: Periodic Heartbeat Fails
    
    Available --> SecurityLock: Enclosure Tamper Triggered
    Charging --> SecurityLock: Cable Cut / Emergency Button Hit
    
    SecurityLock --> Inspection: Security Incident Dispatched
    Inspection --> Available: Clearance Evidence Uploaded
    Inspection --> Maintenance: Physical Repair Ticket Opened
    
    FaultIsolated --> Maintenance: Auto-Ticket Dispatched to CPO
    Maintenance --> Inspection: Repair Work Completed
```

---

### 7. Charger Fault, Tamper & Grid Telemetry Architecture

Coordinated response engine that decouples hardware remediation from customer journey rerouting.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    SENSOR["📡 Edge Telemetry / Tamper Sensor<br/>OCPP Heartbeat • Thermal Spike • Current Leak • Open Door"] --> EVAL{"Threshold Violation?"}
    
    EVAL -->|Normal| CONT["Continue High-Frequency Monitoring"]
    EVAL -->|Anomaly Detected| INCIDENT["⚠️ Log Timestamped Security Incident<br/>Record Station ID, Gun Index, Fault Code, Evidence"]
    
    INCIDENT --> ISOLATE["🛑 Isolate Svc for Faulty Connector Only<br/>Keep adjacent healthy chargers active"]
    
    ISOLATE --> CHECK_ACTIVE{"Active Session or Locked Reservation?"}
    CHECK_ACTIVE -->|Yes| SAFE_SHUT["⚡ Safe Relay Cutoff & Session Snapshot<br/>Auto-calculate prorated energy refund"]
    CHECK_ACTIVE -->|No| DISPATCH_TICKET["🎫 Open Automated CPO Work Order"]
    
    SAFE_SHUT --> DISPATCH_REROUTE["🧠 Autopilot Emergency Engine<br/>Scan for compatible alternative sockets"]
    DISPATCH_REROUTE --> DISPATCH_TICKET
    
    DISPATCH_TICKET --> TECH_DISPATCH["👷 Technician On-Site / Remote Diagnostic"]
    TECH_DISPATCH --> DIAG_CHECK{"Hardware Rectified & Calibrated?"}
    
    DIAG_CHECK -->|No| TECH_DISPATCH
    DIAG_CHECK -->|Yes| RE_CERT["✅ Admin / CPO Verification Sign-Off"]
    RE_CERT --> RESTORE["🟢 Restore Connector to Available Pool"]
    RESTORE --> AUDIT["📝 Append Immutable Incident Resolution Log"]

    classDef healthy fill:#064e3b,stroke:#059669,color:#ecfdf5,stroke-width:2px;
    classDef warning fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;
    classDef danger fill:#4c0519,stroke:#f43f5e,color:#ffe4e6,stroke-width:2px;
    classDef ops fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;

    class CONT,RE_CERT,RESTORE healthy;
    class EVAL,CHECK_ACTIVE,DIAG_CHECK warning;
    class SENSOR,INCIDENT,ISOLATE,SAFE_SHUT danger;
    class DISPATCH_TICKET,DISPATCH_REROUTE,TECH_DISPATCH,AUDIT ops;
```

---

### 8. Principle of Least Privilege Admin Governance Engine

The platform enforces graduated operational intervention. Administrators apply asset- or capability-level restrictions before invoking identity-level suspensions.

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TD
    SIGNAL["🚨 Risk Signal: Dispute, Unpaid Tariff, Tamper, Fraud"] --> COLLECT["📊 Aggregate Evidence Packet<br/>Session logs, metering snapshots, dispute history"]
    COLLECT --> AI_RECOMMEND["🧠 Admin Assistant Proposes Action<br/>Suggests narrowest effective scope, duration & reason"]
    
    AI_RECOMMEND --> SCOPE{"Determine Smallest Impact Scope"}
    
    SCOPE -->|Driver Level| U_SCOPE["EV Driver Scope<br/>Warn • Lock Booking • Freeze AutoPay"]
    SCOPE -->|Host Level| H_SCOPE["Property Host Scope<br/>Hide Listing • Freeze Payout • Re-Verify Site"]
    SCOPE -->|CPO Level| C_SCOPE["Charge Company Scope<br/>Pause Publishing • Hold Settlement • Review Svc"]
    SCOPE -->|Hardware Level| HW_SCOPE["Hardware Socket Scope<br/>Force Offline • Mark Maintenance • Quarantine"]
    
    U_SCOPE --> ADMIN_APPROVAL{"Admin Signs Off Decision?"}
    H_SCOPE --> ADMIN_APPROVAL
    C_SCOPE --> ADMIN_APPROVAL
    HW_SCOPE --> ADMIN_APPROVAL
    
    ADMIN_APPROVAL -->|Approved| APPLY["⚡ Apply Scoped Intervention<br/>Unrelated accounts and hardware remain unaffected"]
    ADMIN_APPROVAL -->|Rejected| CLOSE["Close Review without Intervention"]
    
    APPLY --> NOTIFY_PARTY["📨 Dispatch Formal Notice with Appeal Link"]
    NOTIFY_PARTY --> AUDIT_LOG["🔒 Record Cryptographic Immutable Audit Entry"]

    classDef signal fill:#4c0519,stroke:#f43f5e,color:#ffe4e6,stroke-width:2px;
    classDef decision fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;
    classDef action fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef audit fill:#064e3b,stroke:#059669,color:#ecfdf5,stroke-width:2px;

    class SIGNAL signal;
    class SCOPE,ADMIN_APPROVAL decision;
    class COLLECT,AI_RECOMMEND,U_SCOPE,H_SCOPE,C_SCOPE,HW_SCOPE,CLOSE action;
    class APPLY,NOTIFY_PARTY,AUDIT_LOG audit;
```

---

### 9. RBAC & AI Execution Authority Boundaries

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "fontFamily": "Inter, sans-serif"
  }
}}%%
flowchart TB
    AUTH["🔑 Authenticate & Verify JWT"] --> ROLE{"Resolved Workspace Role"}
    
    ROLE -->|ROLE_DRIVER| R_DRIVER["🚗 EV Owner Workspace<br/>Garage • Booking • AutoPay • Active Trip"]
    ROLE -->|ROLE_HOST| R_HOST["🏢 Property Host Workspace<br/>Properties • Revenue Share • Daily Bookings"]
    ROLE -->|ROLE_COMPANY| R_CPO["⚡ Charge Company Workspace<br/>CPO Network • Dynamic Pricing • Tickets"]
    ROLE -->|ROLE_ADMIN| R_ADMIN["🛡️ Platform Admin Workspace<br/>Compliance • Settlements • Scoped Controls"]

    R_DRIVER --> AGENT_MODE{"Assistant Execution Policy"}
    R_HOST --> AGENT_MODE
    R_CPO --> AGENT_MODE

    AGENT_MODE -->|Tier 1: Informative| T1["Recommend Only<br/>Reasoning & guidance; zero mutation permission"]
    AGENT_MODE -->|Tier 2: Interactive| T2["Ask Before Actions<br/>Drafts transaction; requires manual confirmation"]
    AGENT_MODE -->|Tier 3: Autonomous| T3["Autopilot Execution<br/>Automates within saved budget & safety limits"]

    T3 --> LIMIT_CHECK{"Security & Spending Limits Satisfied?"}
    LIMIT_CHECK -->|✅ Valid| EXEC_OK["Execute Transaction & Log Audit Proof"]
    LIMIT_CHECK -->|❌ Exceeded| ESCALATE["Halt Automation & Escalate to Human Driver"]

    R_ADMIN --> ADMIN_ASSIST["🛡️ Admin Copilot<br/>Governance recommendations require explicit Admin sign-off"]
    ADMIN_ASSIST --> EXEC_OK

    classDef auth fill:#082f49,stroke:#0284c7,color:#e0f2fe,stroke-width:2px;
    classDef roles fill:#14532d,stroke:#16a34a,color:#f0fdf4,stroke-width:2px;
    classDef tiers fill:#2e1065,stroke:#7c3aed,color:#ede9fe,stroke-width:2px;
    classDef guard fill:#78350f,stroke:#f59e0b,color:#fef3c7,stroke-width:2px;

    class AUTH,ROLE auth;
    class R_DRIVER,R_HOST,R_CPO,R_ADMIN roles;
    class AGENT_MODE,T1,T2,T3,ADMIN_ASSIST tiers;
    class LIMIT_CHECK,EXEC_OK,ESCALATE guard;
```

---

### 10. Relational Entity-Relationship Model & Telemetry Schema

```mermaid
%%{init: {
  "theme": "base",
  "themeVariables": {
    "darkMode": true,
    "background": "#0b0f19",
    "primaryColor": "#111827",
    "primaryTextColor": "#f8fafc",
    "primaryBorderColor": "#0ea5e9",
    "lineColor": "#38bdf8",
    "attributeBackgroundColorEven": "#0f172a",
    "attributeBackgroundColorOdd": "#1e293b",
    "fontFamily": "Inter, monospace"
  }
}}%%
erDiagram
    ACCOUNT {
        UUID id PK
        string email
        string password_hash
        string role
        string account_status
        datetime created_at
    }
    EV_PROFILE {
        UUID id PK
        UUID account_id FK
        decimal wallet_balance
        string default_autonomy_mode
    }
    VEHICLE {
        UUID id PK
        UUID profile_id FK
        string make_model
        decimal battery_capacity_kwh
        decimal efficiency_wh_km
        decimal max_dc_power_kw
        string supported_connectors
    }
    HOST_PROFILE {
        UUID id PK
        UUID account_id FK
        decimal host_rating
        string verification_status
        string bank_account_ref
    }
    PROPERTY {
        UUID id PK
        UUID host_id FK
        string property_name
        string street_address
        string city
        decimal latitude
        decimal longitude
        decimal sanctioned_load_kw
        string verification_state
    }
    COMPANY {
        UUID id PK
        UUID account_id FK
        string company_name
        string gst_number
        string verification_status
    }
    STATION {
        UUID id PK
        UUID property_id FK
        UUID operator_id FK
        string station_name
        decimal latitude
        decimal longitude
        string status
    }
    CONNECTOR {
        UUID id PK
        UUID station_id FK
        string standard
        decimal max_power_kw
        decimal tariff_inr_per_kwh
        string current_status
    }
    AUTOPILOT_TRIP {
        UUID id PK
        UUID vehicle_id FK
        string origin_name
        string destination_name
        decimal start_soc
        decimal reserve_soc
        decimal max_budget_inr
        datetime requested_arrival
        datetime projected_arrival
        boolean is_feasible
        string trip_status
    }
    AUTOPILOT_STOP {
        UUID id PK
        UUID trip_id FK
        UUID station_id FK
        decimal arrival_soc
        decimal departure_soc
        int charge_duration_mins
        decimal cost_inr
        string route_provenance
    }
    BOOKING {
        UUID id PK
        UUID connector_id FK
        UUID account_id FK
        datetime slot_start
        datetime slot_end
        string booking_status
        decimal locked_price_inr
    }
    CHARGING_SESSION {
        UUID id PK
        UUID booking_id FK
        decimal energy_delivered_kwh
        decimal peak_power_kw
        decimal final_cost_inr
        string session_state
    }
    ADMIN_AUDIT_LOG {
        UUID id PK
        UUID admin_id FK
        string action_type
        string target_scope
        string before_state_json
        string after_state_json
        string justification
        datetime timestamp
    }

    ACCOUNT ||--o| EV_PROFILE : owns
    ACCOUNT ||--o| HOST_PROFILE : owns
    ACCOUNT ||--o| COMPANY : operates
    EV_PROFILE ||--o{ VEHICLE : registers
    HOST_PROFILE ||--o{ PROPERTY : lists
    PROPERTY ||--o{ STATION : hosts
    COMPANY ||--o{ STATION : manages
    STATION ||--o{ CONNECTOR : contains
    VEHICLE ||--o{ AUTOPILOT_TRIP : drives
    AUTOPILOT_TRIP ||--o{ AUTOPILOT_STOP : schedules
    STATION ||--o{ AUTOPILOT_STOP : serves
    CONNECTOR ||--o{ BOOKING : reserves
    ACCOUNT ||--o{ BOOKING : creates
    BOOKING ||--o| CHARGING_SESSION : tracks
    ACCOUNT ||--o{ ADMIN_AUDIT_LOG : audits
```

---

## 📡 Binary Protocols, Telemetry & Frame Schemas

### 1. ISO-8601 Time-Tagged Live Telemetry Frame

High-frequency socket telemetry serialized over WebSockets / MQTT for real-time Autopilot recalculations:

```json
{
  "protocol": "VIDYUT_OCPP_EXT_v2.1",
  "timestamp_utc": "2026-08-23T10:36:00.184Z",
  "station_uuid": "e6a2b851-9dc4-4d8b-b8ef-52c418f72c39",
  "socket_index": 1,
  "connector_standard": "CCS2",
  "status": "CHARGING",
  "telemetry": {
    "voltage_v": 412.8,
    "current_a": 145.3,
    "active_power_kw": 59.98,
    "gun_temperature_celsius": 38.4,
    "delivered_kwh": 24.812,
    "current_soc_percent": 68.4,
    "target_soc_percent": 80.0,
    "time_remaining_seconds": 780
  },
  "safety_mesh": {
    "ground_isolation_resistance_kohm": 940,
    "enclosure_tamper_detected": false,
    "emergency_stop_triggered": false,
    "grid_frequency_hz": 50.02
  }
}
```

### 2. Autopilot Route Optimization Constraint Payload

```json
{
  "origin": { "lat": 26.8467, "lng": 80.9462, "label": "Lucknow" },
  "destination": { "lat": 23.2599, "lng": 77.4126, "label": "Bhopal" },
  "vehicle_profile": {
    "model": "Tata Nexon EV Max",
    "usable_kwh": 40.5,
    "efficiency_wh_km": 142.0,
    "max_dc_kw": 50.0,
    "charging_loss_factor": 0.08
  },
  "constraints": {
    "initial_soc": 55.0,
    "safety_reserve_soc": 10.0,
    "max_budget_inr": 1800.00,
    "arrive_by_iso": "2026-08-23T19:30:00Z",
    "autonomy_tier": "ASK_BEFORE_ACTION",
    "optimization_mode": "FASTEST"
  }
}
```

---

## 🎛️ Implemented Workspaces & Cockpits

<details open>
<summary><b>🚗 1. EV Owner Experience</b></summary>

- **Interactive Garage**: Configure usable battery capacity, Wh/km efficiency, max DC power limits, and supported connectors.
- **Autopilot NLP Planner**: Parses queries like *"Drive my Nexon EV to Bhopal, start at 50%, arrive before 7 PM, keep ₹1,500 budget"*.
- **Vehicle Comparator**: Automatically compares garage vehicles on a route and highlights the optimal car.
- **Continuous 72h Journey Recovery**: Active road trips persist on the dashboard even across reloads or network drops.
- **Self-Healing Rerouting**: Automatic connector re-selection and booking migration upon hardware faults.
</details>

<details>
<summary><b>🏢 2. Property Host Experience</b></summary>

- **Property Onboarding**: Multi-step site submission with geotagging, electricity bill uploads, and video proof.
- **Occupancy & Demand**: Real-time socket status streaming directly from active database sessions.
- **Host Copilot**: AI-assisted revenue optimization, tariff recommendations, and maintenance scheduling.
- **Green Finance & Solar RESCO**: Financial modeling for solar PPA, capex investment, and subsidy eligibility.
</details>

<details>
<summary><b>⚡ 3. ChargePoint Operator (CPO) Experience</b></summary>

- **Network Control Room**: Monitor station status, fault codes, live queues, and charging curves.
- **Granular Maintenance Isolation**: Isolate a faulty connector without taking the entire multi-gun station offline.
- **Expansion Intelligence**: Read-only grid analytics ranking top high-potential sites based on power readiness and route traffic.
- **Dynamic Tariffing**: Configurable time-of-day and congestion pricing models.
</details>

<details>
<summary><b>🛡️ 4. Admin Governance & Control Plane</b></summary>

- **Capability-Scoped RBAC**: Fine-grained administrator boundaries (Verification Admin, Settlement Admin, Super Admin).
- **Graduated Scoped Interventions**: Restrict specific sockets, listings, or capabilities before identity suspension.
- **Immutable Cryptographic Audit Trail**: Every administrative action logs actor, before/after JSON states, and justifications.
- **Network AI Memory**: Platform-wide intelligence tracking route outcomes and incident resolution times.
</details>

---

## 🚀 Quickstart & Concurrent Launch

### Prerequisites

| Tool | Recommended Version |
| :--- | :--- |
| **Java JDK** | OpenJDK 17 LTS or higher |
| **Node.js** | Node.js 18 LTS+ (with npm 9+) |
| **Python** | Python 3.10 or 3.11 |
| **PostgreSQL** | PostgreSQL 15+ |
| **Docker** (Optional) | For running the local OSRM routing container |

---

### One-Command Concurrent Launch

The root launcher boots the `vidyut-osrm` container, compiles the Spring Boot backend, starts the FastAPI Python AI agent, and launches the Vite React frontend concurrently:

```powershell
# 1. Install root dependencies
npm install

# 2. Run all microservices concurrently
npm run dev
```

---

### Manual Step-by-Step Launch

<details>
<summary><b>Click to expand individual subsystem startup instructions</b></summary>

#### 1. Start PostgreSQL & OSRM Engine
```powershell
# Start local OSRM India router (Port 5000)
docker run -d --name vidyut-osrm -p 5000:5000 osrm/osrm-backend osrm-routed --algorithm mld /data/india-latest.osrm
```

#### 2. Launch Spring Boot Domain API (Port 8080)
```powershell
cd vidyut-backend
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/vidyut_db"
$env:SPRING_DATASOURCE_USERNAME = "postgres"
$env:SPRING_DATASOURCE_PASSWORD = "your_postgres_password"
mvn spring-boot:run
```

#### 3. Launch Python ADK AI Agent (Port 8001)
```powershell
cd vidyut-ai\agent
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:GOOGLE_API_KEY = "your_gemini_api_key"
python -m vidyut_agent
```

#### 4. Launch React 19 Web Cockpit (Port 5173)
```powershell
cd vidyut-web
npm install
npm run dev
```

#### 5. Launch Mobile Application (Expo 57)
```powershell
cd vidyut-mobile
npm install
npm run android
```

</details>

---

### Local Endpoint Directory

| Microservice | Protocol | Local URL |
| :--- | :--- | :--- |
| **Backend REST API** | `HTTP / JSON` | `http://localhost:8080/api` |
| **Swagger / OpenAPI** | `HTTP / HTML` | `http://localhost:8080/swagger-ui.html` |
| **Web App Cockpit** | `HTTP / SPA` | `http://localhost:5173` |
| **Python ADK Agent** | `HTTP / FastMCP` | `http://127.0.0.1:8001` |
| **OSRM Route Server** | `HTTP / OSRM` | `http://localhost:5000` |

---

### Taskmaster fault-recovery demo

Use one `FULL_AUTOPILOT` journey for the submission recording:

1. Sign in as the seeded EV Owner and build a feasible Lucknow/Kanpur-to-Bhopal trip.
2. Confirm the plan and start journey monitoring.
3. Select **Simulate charger fault** in Journey controls.
4. Verify the EV timeline shows the old booking released, a compatible replacement reserved, the route updated, and **Operations incident propagated**.
5. Refresh the Company workspace and verify the maintenance ticket and faulted connector.
6. Refresh Admin → Network operations and verify the incident.
7. Open Admin → Audit trail and verify `AUTOPILOT INCIDENT DETECTED` with actor `SYSTEM AGENT`.

The simulation is a single backend transaction: connector health changes to `FAULT`, Autopilot applies the saved autonomy policy, the booking and route are recovered, the Company work order is created, Host/Company notifications are stored, and Admin receives incident plus audit evidence. `ASK_BEFORE_ACTIONS` prepares the replacement but cannot reserve it until the driver approves; only `FULL_AUTOPILOT` executes the replacement automatically.

---

## 🧪 Comprehensive Verification & Test Suite

Run full automated test verification across all platform modules:

```powershell
# From the repository root
.\scripts\verify.ps1
```

The script runs the Spring suite, Python agent suite, web lint and production web build. Equivalent individual commands are below.

```powershell
# 1. Backend: 19 unit & integration test suites with Flyway migration checks
cd vidyut-backend
mvn test

# 2. Python AI Agent: Tool calling, provider fallback & token limit unit tests
cd ..\vidyut-ai\agent
.\.venv\Scripts\python -m unittest discover -s tests -v

# 3. Web Client: TypeScript verification, ESLint, and production Vite bundle
cd ..\..\vidyut-web
npm run lint
npm run build

# 4. Mobile Client: Typecheck & Expo manifest validation
cd ..\vidyut-mobile
npm run typecheck
npx expo config --type public
```

---

## 🗺️ API Surface Reference

```text
/api/auth/**                 -> JWT Token Issuance, Google OAuth, Role Provisioning
/api/ev/autopilot/**         -> NLP Journey Parsing, Multi-Stop Matrix Optimization, Reroute Engine
/api/ev/vehicles/**          -> Garage Management, Non-Linear SoC Curves, Connector Specs
/api/ev/bookings/**          -> Real-Time Connector Locks, Payment Gateways & QR Verification
/api/ev/payments/**          -> Wallet Ledger, AutoPay Thresholds, Prorated Refund Engine
/api/routing/**              -> Raw OSRM Road Geometry, Elevation Profiles & Haversine Fallbacks
/api/host/**                 -> Property Listings, Bank Accounts, Occupancy & Daily Earnings
/api/host/marketplace/**     -> CPO Discovery, Site Feasibility Surveys, Capex Proposals
/api/company/**              -> CPO Stations, Socket Health, Dispatch Tickets & Dynamic Pricing
/api/company/marketplace/**  -> Hardware Product Approvals, Site Ranking & Expansion Intel
/api/admin/auth/**           -> Isolated Super Admin Authentication
/api/admin/portal/**         -> Scoped Restrictions, Platform Audit Logs, Incident Mediation
```

---

<div align="center">

**Built with ❤️&⚡for the Future of Sustainable Indian Mobility**

*Vidyut EV Platform • Enterprise Architecture Reference*

</div>
