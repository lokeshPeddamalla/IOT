import json

# Valid end keywords
valid_end_keywords = {
    "S", "START", "OPTIONAL", "SENSOR", "ACTUATOR", "DEVICE", "OEM", "MODE", "LOCATION", "D", "NAME", "DOMAIN",
    "WARRANTY", "INSTALLATION", "SW VERSION", "HW VERSION", "O", "SOCKET", "M", "NAME VAL", "Z", "DATA",
    "ACTUATOR INFO", "VALUE", "UNIT VAL", "AREA", "X", "DESC", "SENSOR TYPE", "STRUCT X", "BATTERY", "GRAPH",
    "DATA TYPE", "PIE", "BAR", "GRAPH TYPE", "CORD1", "CORD2", "BOOLEAN", "NUMERIC", "STRING", "IMAGE", "AUDIO",
    "VIDEO", "MAP", "COLOR", "DATE", "TIME", "RANGE", "TUPLE", "UNIT", "R", "RVAL", "MIN", "MAX", "STEP",
    "UNIT VAL", "OPERATION", "EXCEPT", "TVAL", "OPTION", "OVAL", "SIZE", "IMAGE ATTRIB", "LENGTH", "WIDTH", "SRC",
    "IMAGE TYPE", "PLAYER TYPE", "PLAYER ATTRIB", "LOOP", "MUTE", "ACTUATOR", "Y", "ACTUATOR TYPE", "STRUCT Y"
}

# Range categories
range_categories = [
    {"MIN", "MAX", "STEP", "OPERATION"},
    {"MIN", "MAX", "STEP", "OPERATION", "UNIT VAL"},
    {"MIN", "MAX", "STEP", "OPERATION", "UNIT VAL", "EXCEPT"}
]

# Recursive function to classify JSON content
def classify_json(json_object, parent_key=""):
    classifications = []

    for key, value in json_object.items():
        full_key = f"{parent_key} - {key}" if parent_key else key
        category = classify_value(key, value)

        classifications.append((full_key, value, category))

        if isinstance(value, dict):
            classifications.extend(classify_json(value, full_key))

    return classifications

# Function to classify a single key-value pair
def classify_value(key, value):
    if key in {"NAME", "DOMAIN", "INSTALLATION", "MODE"}:
        return "String"
    elif key == "RANGE" and isinstance(value, list):
        if any(set(value).issubset(category) for category in range_categories):
            return f"RANGE Category: {', '.join(map(str, value))}"
        return "Invalid RANGE Category"
    elif key == "OPTION" and all(isinstance(item, str) for item in value):
        return f"Option valid {len(value)} strings"
    elif key == "BOOLEAN" and len(value) == 2 and all(isinstance(item, str) for item in value):
        return "[String, String] - BOOLEAN"
    return "Unknown Category"

# Test JSON
json_data = {
    "DEVICE": {
        "NAME": "Air conditioner",
        "DOMAIN": "Smart home",
        "INSTALLATION": "Fixed"
    },
    "MODE": {
        "COOL": {"Temperature": [16, "C"], "Fan": ["medium"]},
        "DRY": {"Temperature": [25, "C"], "Fan": ["high"]}
    },
    "ACTUATOR": {
        "Temperature": {"NUMERIC": {"RANGE": [14, 30, 0.5, "+", "C"]}},
        "Fan": {"STRING": {"OPTION": ["low", "medium", "high"]}},
        "Swing": {"BOOLEAN": ["up", "down"]},
        "Power": {"BOOLEAN": ["on", "off"]}
    }
}

# Process and classify JSON
classifications = classify_json(json_data)

# Display classifications
for key, value, category in classifications:
    print(f"{key}: Value - {value}, Category - {category}")
