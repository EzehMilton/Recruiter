package com.recruiter.ai;

import java.util.List;
import java.util.Map;

/**
 * Provides sector-specific skill terms that supplement the generic heuristic skill dictionary
 * when a sector is selected. These terms are injected as {@code additionalSkills} into
 * {@code TextProfileHeuristicsService.extractSkills()} for both the job description and
 * candidate CV, so sector-specific vocabulary is matched consistently on both sides.
 *
 * Terms here are chosen because they are genuinely sector-relevant and either absent from
 * or under-represented in the generic dictionary. The generic dictionary already covers
 * broad transferable skills (Git, Excel, Project Management, etc.) so those are not repeated.
 *
 * To add a new sector: add a new entry to SECTOR_SKILLS. No code changes elsewhere are needed.
 */
public final class SectorSkillDictionary {

    private SectorSkillDictionary() {
    }

    private static final Map<Sector, List<String>> SECTOR_SKILLS = Map.ofEntries(

            Map.entry(Sector.IT_AND_TECHNOLOGY, List.of(
                    // Languages
                    "C#", ".NET", "Go", "Golang", "Rust", "Ruby", "Kotlin", "Swift", "PHP", "Scala",
                    // Frameworks / libraries
                    "Vue.js", "Next.js", "Nuxt.js", "FastAPI", "Django", "Flask", "Rails",
                    "GraphQL", "gRPC", "Spring Framework",
                    // Data / messaging
                    "Redis", "Elasticsearch", "Kafka", "RabbitMQ", "Snowflake", "dbt",
                    "Apache Spark", "Hadoop", "BigQuery",
                    // DevOps / infra
                    "Jenkins", "GitHub Actions", "GitLab CI", "CircleCI", "Ansible",
                    "Helm", "Prometheus", "Grafana", "Datadog", "Splunk", "Nginx",
                    // Practices
                    "TDD", "BDD", "SOLID", "Design Patterns", "System Design",
                    "Domain-Driven Design", "Event-Driven Architecture",
                    // Tools
                    "JIRA", "Confluence", "IntelliJ"
            )),

            Map.entry(Sector.HEALTHCARE, List.of(
                    // Registrations / regulators
                    "NMC", "GMC", "HCPC", "GPhC", "CQC", "NMC Pin",
                    // Specialisms
                    "Mental Health", "Physiotherapy", "Radiography", "Occupational Therapy",
                    "Paramedic", "Midwifery", "Palliative Care", "Dementia Care",
                    "Paediatrics", "Geriatrics", "Oncology", "Theatre Nursing",
                    "District Nursing", "Community Care", "CAMHS",
                    // Clinical procedures
                    "Venepuncture", "Wound Care", "Catheter Care", "ECG", "Triage",
                    "IV Cannulation", "Medication Management", "Discharge Planning",
                    // Compliance / frameworks
                    "HIPAA", "Enhanced DBS", "MDT", "CPA", "Care Act",
                    "Mental Capacity Act", "Deprivation of Liberty Safeguards"
            )),

            Map.entry(Sector.FINANCE, List.of(
                    // Qualifications
                    "ACA", "ACCA", "CIMA", "CFA", "FRM", "CPA", "CIIA", "AAT",
                    // Skills / tools
                    "Financial Modelling", "DCF", "LBO", "Bloomberg Terminal",
                    "Power BI", "Tableau", "Xero", "Sage", "QuickBooks",
                    // Standards / regulation
                    "IFRS", "GAAP", "Solvency II", "Basel III", "MiFID II",
                    "FCA", "AML", "KYC", "FATCA",
                    // Domains
                    "Private Equity", "Investment Banking", "Hedge Fund",
                    "Treasury", "Fund Accounting", "Credit Risk", "Market Risk",
                    "Derivatives", "Fixed Income", "Equity Research",
                    // Processes
                    "Month End Close", "Variance Analysis", "P&L",
                    "Balance Sheet Reconciliation", "Cash Flow Forecasting"
            )),

            Map.entry(Sector.EDUCATION, List.of(
                    // Qualifications / registration
                    "QTS", "PGCE", "QTLS", "HLTA", "NQT",
                    // Frameworks / stages
                    "EYFS", "KS1", "KS2", "KS3", "KS4", "A-Level", "GCSE", "BTEC",
                    "IB", "International Baccalaureate", "NVQ",
                    // SEND / inclusion
                    "SEND", "SEMH", "EHCP", "SEN Support", "SLCN",
                    // Roles / responsibilities
                    "Head of Department", "Form Tutor", "Cover Supervisor",
                    "Subject Leader", "SENCO",
                    // Pedagogy / process
                    "Differentiation", "Parent Liaison", "Behaviour Management",
                    "Lesson Observation", "CPD", "PSHE", "Safeguarding Level 3",
                    // Inspection
                    "Ofsted", "SIP"
            )),

            Map.entry(Sector.SALES_AND_MARKETING, List.of(
                    // Tools / platforms
                    "HubSpot", "Marketo", "Pardot", "Google Analytics", "Google Ads",
                    "Meta Ads", "LinkedIn Sales Navigator", "Outreach", "Salesloft",
                    "Salesforce CRM",
                    // Sales motions
                    "Cold Calling", "Lead Generation", "Pipeline Management",
                    "Business Development", "Account Management", "Territory Management",
                    "SDR", "BDR", "Outbound Sales", "Inbound Sales",
                    "Consultative Selling", "Solution Selling", "MEDDIC",
                    // Marketing disciplines
                    "Digital Marketing", "PPC", "Paid Social", "Email Marketing",
                    "Content Marketing", "ABM", "SEO", "SEM",
                    // Metrics
                    "Quota", "Revenue Target", "MRR", "ARR", "Conversion Rate",
                    "Customer Acquisition Cost", "Churn", "NPS"
            )),

            Map.entry(Sector.MANUAL_LABOUR, List.of(
                    // Licences / cards
                    "IPAF", "PASMA", "NPORS", "CPCS", "LGV", "HGV", "Class 1", "Class 2",
                    "HIAB", "Moffett", "Counterbalance Forklift", "Reach Truck", "Pump Truck",
                    // Certifications
                    "Asbestos Awareness", "Confined Spaces", "Working at Height",
                    "Abrasive Wheels", "Slinger Signaller", "Banksman",
                    "Streetworks", "COSHH", "Toolbox Talk",
                    // Conditions
                    "Night Shift", "Continental Shift", "12 Hour Shift",
                    "Lone Working", "Outdoor Working",
                    // Documents
                    "Right to Work", "Enhanced DBS", "CRB"
            )),

            Map.entry(Sector.RETAIL, List.of(
                    // Store operations
                    "Merchandising", "Visual Merchandising", "Planogram",
                    "Stock Control", "Stock Replenishment", "Stocktaking",
                    "EPOS", "Till Operations", "Loss Prevention", "Shrinkage",
                    "Click and Collect", "Returns Processing",
                    // Compliance
                    "Challenge 25", "Age Verification", "Food Hygiene Certificate",
                    // Management
                    "Store Manager", "Assistant Manager", "Department Manager",
                    "Duty Manager", "Team Leader",
                    // Commercial
                    "Retail KPIs", "Sales Targets", "Upselling",
                    "Customer Experience", "Net Promoter Score",
                    // Availability
                    "Weekend Working", "Bank Holiday", "Peak Trading"
            )),

            Map.entry(Sector.CONSTRUCTION, List.of(
                    // Site qualifications
                    "SMSTS", "SSSTS", "NVQ Construction", "NVQ Level 3",
                    // Professional
                    "Quantity Surveying", "RICS", "CIOB",
                    // Contracts
                    "JCT", "NEC", "NEC3", "NEC4",
                    // Project tools
                    "MS Project", "Asta Powerproject",
                    // Commercial
                    "Bill of Quantities", "Estimating", "Tendering",
                    "Valuations", "Retention", "Subcontractor Management",
                    "Specification Writing",
                    // Structural / civil
                    "Civil Engineering", "Groundworks", "Fit Out",
                    "Structural Steel", "Reinforced Concrete", "Piling",
                    "Mechanical and Electrical", "M&E Coordination",
                    // Site roles
                    "Site Agent", "Site Manager", "Contracts Manager",
                    "Commercial Manager", "Project Surveyor"
            )),

            Map.entry(Sector.MANUFACTURING, List.of(
                    // CI / quality frameworks
                    "Lean Manufacturing", "Six Sigma", "Green Belt", "Black Belt",
                    "5S", "Kaizen", "TPM", "OEE", "Jidoka", "Poka-Yoke",
                    "Value Stream Mapping", "SMED", "JIT", "Kanban",
                    // Quality standards
                    "ISO 9001", "ISO 14001", "IATF 16949", "AS9100",
                    "FMEA", "SPC", "PPAP", "APQP", "CAPA",
                    // Processes / equipment
                    "Injection Moulding", "CNC Machining", "Welding",
                    "Assembly Line", "Quality Inspection", "Quality Control",
                    // Planning / systems
                    "Production Planning", "MRP", "MRPII", "ERP Manufacturing",
                    "Statistical Process Control",
                    // Roles
                    "Production Manager", "Shift Manager", "Quality Manager",
                    "Continuous Improvement Manager"
            )),

            Map.entry(Sector.GREEN_ECONOMY, List.of(
                    // Renewable energy
                    "Solar PV", "Wind Energy", "Offshore Wind", "Onshore Wind",
                    "Heat Pump", "Hydroelectric", "Geothermal", "Tidal Energy",
                    "Energy Storage", "Battery Storage", "Smart Grid",
                    "Power Purchase Agreement", "Feed-in Tariff",
                    "EV Infrastructure", "Electric Vehicle", "Charge Point",
                    "Hydrogen", "Green Hydrogen",
                    // Environmental consultancy
                    "Environmental Impact Assessment", "EIA",
                    "Life Cycle Assessment", "LCA",
                    "Ecological Impact Assessment",
                    "Phase 1 Habitat Survey", "Extended Phase 1",
                    "Biodiversity Net Gain", "BNG",
                    "Natural Capital", "Habitat Management",
                    "Contaminated Land", "Soil Remediation",
                    "Environmental Permitting", "IEMA",
                    // Carbon / net zero
                    "Net Zero", "Carbon Neutral", "Decarbonisation",
                    "Carbon Footprinting", "GHG Protocol",
                    "Science Based Targets", "SBTi", "PAS 2060",
                    "Carbon Markets", "Carbon Credits", "Emissions Trading",
                    "UK ETS", "ESOS", "SECR",
                    // ESG / sustainable finance
                    "ESG", "ESG Reporting", "GRI", "TCFD", "CSRD", "SFDR", "CDP",
                    "Green Bonds", "Sustainable Finance", "Impact Investing",
                    "Climate Risk", "Stranded Assets",
                    // Standards / frameworks
                    "ISO 14001", "ISO 50001", "BREEAM", "LEED", "Passivhaus",
                    "NEBOSH Environmental", "CEnv",
                    // Circular economy / waste
                    "Circular Economy", "Waste Management", "WEEE",
                    "Resource Efficiency", "Zero Waste",
                    // Social inclusion
                    "Just Transition", "Community Engagement",
                    "Social Value", "Social Enterprise"
            )),

            Map.entry(Sector.GENERIC, List.of())
    );

    /**
     * Returns the sector-specific skill terms for the given sector.
     * Returns an empty list for {@link Sector#GENERIC} or any null input.
     */
    public static List<String> getSkills(Sector sector) {
        if (sector == null) {
            return List.of();
        }
        return SECTOR_SKILLS.getOrDefault(sector, List.of());
    }
}
