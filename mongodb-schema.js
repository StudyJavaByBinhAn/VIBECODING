// ====================================================================
// DENTAL BOOKING SYSTEM - MongoDB Collections & Indexes
// Database: dental_booking_db
// ====================================================================

// Switch to database
use dental_booking_db;

// ====================================================================
// COLLECTION 1: patient_dental_records
// Purpose: Flexible dental patient records with nested medical history
// ====================================================================

db.createCollection("patient_dental_records", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["patient_id", "user_id", "full_name"],
      properties: {
        _id: { bsonType: "objectId" },
        patient_id: {
          bsonType: "long",
          description: "Reference to MySQL patients.patient_id"
        },
        user_id: {
          bsonType: "long",
          description: "Reference to MySQL users.user_id"
        },
        full_name: { bsonType: "string" },
        date_of_birth: { bsonType: "date" },
        gender: {
          enum: ["MALE", "FEMALE", "OTHER"],
          description: "Gender"
        },
        
        // === TOOTH CHART (Sơ đồ Răng) ===
        tooth_chart: {
          bsonType: "object",
          properties: {
            teeth: {
              bsonType: "array",
              items: {
                bsonType: "object",
                properties: {
                  tooth_id: { bsonType: "string", description: "FDI notation (11-48)" },
                  status: {
                    enum: ["HEALTHY", "CAVITY", "ROOT_CANAL", "MISSING", "IMPLANT", "CROWN"],
                    description: "Tooth condition"
                  },
                  restoration_type: { bsonType: ["string", "null"] },
                  notes: { bsonType: "string" }
                }
              }
            },
            last_updated: { bsonType: "date" },
            updated_by_doctor_id: { bsonType: "long" }
          }
        },
        
        // === TREATMENT HISTORY (Lịch sử Điều trị - Nested Array) ===
        treatment_history: {
          bsonType: "array",
          items: {
            bsonType: "object",
            properties: {
              treatment_id: { bsonType: "objectId" },
              booking_id: { bsonType: "long", description: "Reference to MySQL bookings" },
              date: { bsonType: "date" },
              doctor_id: { bsonType: "long" },
              doctor_name: { bsonType: "string" },
              visit_type: {
                enum: ["CONSULTATION", "TREATMENT", "FOLLOW_UP", "EMERGENCY"],
                description: "Type of visit"
              },
              treatment_type: {
                enum: [
                  "CAVITY_FILLING", "ROOT_CANAL", "CROWN", "IMPLANT",
                  "SCALING_POLISHING", "EXTRACTION", "ORTHODONTIC",
                  "FOLLOW_UP_CHECK", "CONSULTATION", "COSMETIC"
                ]
              },
              teeth_treated: {
                bsonType: "array",
                items: { bsonType: "string" },
                description: "Array of tooth IDs treated"
              },
              description: { bsonType: "string" },
              materials_used: {
                bsonType: "array",
                items: {
                  bsonType: "object",
                  properties: {
                    material_name: { bsonType: "string" },
                    quantity: { bsonType: "int" },
                    brand: { bsonType: "string" }
                  }
                }
              },
              cost: { bsonType: "long", description: "Cost in VND" },
              notes: { bsonType: "string" },
              follow_up_required: { bsonType: "bool" },
              follow_up_date: { bsonType: ["date", "null"] }
            }
          }
        },
        
        // === TREATMENT PLAN (Kế hoạch Điều trị) ===
        treatment_plan: {
          bsonType: "object",
          properties: {
            created_date: { bsonType: "date" },
            created_by_doctor_id: { bsonType: "long" },
            plan_description: { bsonType: "string" },
            estimated_duration: { bsonType: "string" },
            estimated_cost: { bsonType: "long" },
            priority_treatments: {
              bsonType: "array",
              items: {
                bsonType: "object",
                properties: {
                  treatment: { bsonType: "string" },
                  teeth: { bsonType: "array", items: { bsonType: "string" } },
                  priority: { enum: ["HIGH", "MEDIUM", "LOW"] },
                  estimated_cost: { bsonType: "long" }
                }
              }
            },
            notes: { bsonType: "string" },
            status: { enum: ["ACTIVE", "COMPLETED", "CANCELLED"] }
          }
        },
        
        // === MEDICAL CONDITIONS (Dị ứng & Bệnh Kèm Theo) ===
        medical_conditions: {
          bsonType: "object",
          properties: {
            allergies: { bsonType: "array", items: { bsonType: "string" } },
            chronic_diseases: { bsonType: "array", items: { bsonType: "string" } },
            medications: { bsonType: "array", items: { bsonType: "string" } },
            notes: { bsonType: "string" }
          }
        },
        
        // === RADIOGRAPHS (X-quang) ===
        radiographs: {
          bsonType: "array",
          items: {
            bsonType: "object",
            properties: {
              radiograph_id: { bsonType: "objectId" },
              date: { bsonType: "date" },
              type: { enum: ["PERIAPICAL", "PANORAMIC", "BITEWINGS"] },
              url: { bsonType: "string", description: "S3 URL to image" },
              findings: { bsonType: "string" },
              uploaded_by_doctor_id: { bsonType: "long" }
            }
          }
        },
        
        // === AUDIT & TIMESTAMPS ===
        created_at: { bsonType: "date" },
        updated_at: { bsonType: "date" },
        created_by_doctor_id: { bsonType: "long" }
      }
    }
  }
});

// Create Indexes for patient_dental_records
// ✅ Primary lookups
db.patient_dental_records.createIndex({ "patient_id": 1 }, { unique: true });
db.patient_dental_records.createIndex({ "user_id": 1 }, { unique: true });

// ✅ Recent updates (dashboard)
db.patient_dental_records.createIndex({ "updated_at": -1 });

// ✅ Analytics queries
db.patient_dental_records.createIndex({ "date_of_birth": 1 });

// ✅ Treatment history queries (important for timeline view)
db.patient_dental_records.createIndex({ "treatment_history.date": -1 });
db.patient_dental_records.createIndex({ "treatment_history.doctor_id": 1, "treatment_history.date": -1 });

// ✅ Treatment plan status (for active/pending plans)
db.patient_dental_records.createIndex({
  "treatment_plan.status": 1,
  "treatment_plan.created_date": -1
});

// ✅ Medical alerts (allergies, chronic diseases)
db.patient_dental_records.createIndex({ "medical_conditions.allergies": 1 });
db.patient_dental_records.createIndex({ "medical_conditions.chronic_diseases": 1 });

// ✅ Radiographs timeline
db.patient_dental_records.createIndex({ "radiographs.date": -1 });

console.log("✅ Indexes created for patient_dental_records");

// ====================================================================
// COLLECTION 2: dental_materials_inventory
// Purpose: Track dental materials stock and expiry
// ====================================================================

db.createCollection("dental_materials_inventory", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["material_id", "material_name", "category"],
      properties: {
        _id: { bsonType: "objectId" },
        material_id: {
          bsonType: "string",
          description: "Unique material identifier (e.g., COMPOSITE_3M_001)"
        },
        material_name: { bsonType: "string" },
        category: {
          enum: ["RESTORATIVE", "CROWN", "FILLING", "INSTRUMENT", "CHEMICAL", "OTHER"],
          description: "Material category"
        },
        manufacturer: { bsonType: "string" },
        batch_number: { bsonType: "string" },
        expiry_date: { bsonType: "date" },
        quantity_in_stock: { bsonType: "int" },
        unit: { enum: ["SYRINGE", "TUBE", "BOTTLE", "BOX", "PACK", "PIECE"] },
        unit_cost: { bsonType: "long", description: "Cost in VND" },
        reorder_level: { bsonType: "int", description: "Minimum stock alert" },
        supplier_id: { bsonType: "string" },
        created_at: { bsonType: "date" },
        updated_at: { bsonType: "date" },
        
        // Stock movements (audit trail)
        stock_movements: {
          bsonType: "array",
          items: {
            bsonType: "object",
            properties: {
              movement_id: { bsonType: "objectId" },
              movement_type: { enum: ["IN", "OUT", "ADJUSTMENT"] },
              quantity: { bsonType: "int" },
              reason: { bsonType: "string" },
              timestamp: { bsonType: "date" },
              recorded_by_user_id: { bsonType: "long" }
            }
          }
        }
      }
    }
  }
});

// Create Indexes for dental_materials_inventory
db.dental_materials_inventory.createIndex({ "material_id": 1 }, { unique: true });
db.dental_materials_inventory.createIndex({ "category": 1 });
db.dental_materials_inventory.createIndex({ "expiry_date": 1 });

// ✅ Alert for low stock
db.dental_materials_inventory.createIndex({
  "quantity_in_stock": 1,
  "reorder_level": 1
});

// ✅ Expired or expiring soon
db.dental_materials_inventory.createIndex({ "expiry_date": 1 }) 
  .then(() => console.log("✅ Expiry alert index created"));

console.log("✅ Indexes created for dental_materials_inventory");

// ====================================================================
// COLLECTION 3: clinical_templates (Optional)
// Purpose: Store reusable clinical note templates
// ====================================================================

db.createCollection("clinical_templates", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["template_name", "treatment_type"],
      properties: {
        _id: { bsonType: "objectId" },
        template_name: { bsonType: "string" },
        treatment_type: { bsonType: "string" },
        template_content: { bsonType: "string", description: "Template text with placeholders" },
        created_by_doctor_id: { bsonType: "long" },
        is_shared: { bsonType: "bool", description: "Available to all doctors" },
        created_at: { bsonType: "date" }
      }
    }
  }
});

db.clinical_templates.createIndex({ "treatment_type": 1 });
db.clinical_templates.createIndex({ "created_by_doctor_id": 1 });

console.log("✅ Indexes created for clinical_templates");

// ====================================================================
// VERIFICATION QUERIES
// ====================================================================

// Verify collections exist
console.log("\n=== Collections Created ===");
db.getCollectionNames().forEach(col => {
  console.log("✅", col);
});

// Check all indexes
console.log("\n=== All Indexes ===");
db.patient_dental_records.getIndexes().forEach(idx => console.log("📌", JSON.stringify(idx)));
db.dental_materials_inventory.getIndexes().forEach(idx => console.log("📌", JSON.stringify(idx)));

// ====================================================================
// SAMPLE QUERIES (for development testing)
// ====================================================================

// 1. Find patient dental record by patient_id
// db.patient_dental_records.findOne({ patient_id: 123 });

// 2. Find recent treatments
// db.patient_dental_records.aggregate([
//   { $match: { patient_id: 123 } },
//   { $unwind: "$treatment_history" },
//   { $sort: { "treatment_history.date": -1 } },
//   { $limit: 5 }
// ]);

// 3. Find active treatment plans
// db.patient_dental_records.find({ "treatment_plan.status": "ACTIVE" });

// 4. Find materials expiring within 3 months
// db.dental_materials_inventory.find({
//   expiry_date: { 
//     $gte: new Date(), 
//     $lte: new Date(new Date().getTime() + 90 * 24 * 60 * 60 * 1000) 
//   }
// });

// 5. Find low stock items
// db.dental_materials_inventory.find({
//   $expr: { $lte: ["$quantity_in_stock", "$reorder_level"] }
// });

console.log("\n✅ MongoDB Schema & Indexes Initialization Complete!");
