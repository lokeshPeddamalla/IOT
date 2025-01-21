import json

valid_end_keywords = [
    "S", "START", "OPTIONAL", "SENSOR", "ACTUATOR", "DEVICE", "OEM", "MODE", "LOCATION", "D", "NAME", "DOMAIN",
        "WARRANTY", "INSTALLATION", "SW VERSION", "HW VERSION", "O", "SOCKET", "M", "NAME VAL", "Z", "DATA",
        "ACTUATOR INFO", "VALUE", "UNIT VAL", "AREA", "X", "DESC", "SENSOR TYPE", "STRUCT X", "BATTERY", "GRAPH",
        "DATA TYPE", "PIE", "BAR", "GRAPH TYPE", "CORD1", "CORD2", "BOOLEAN", "NUMERIC", "STRING", "IMAGE", "AUDIO",
        "VIDEO", "MAP", "COLOR", "DATE", "TIME", "RANGE", "TUPLE", "UNIT", "R", "RVAL", "MIN", "MAX", "STEP",
        "UNIT VAL", "OPERATION", "EXCEPT", "TVAL", "OPTION", "OVAL", "SIZE", "IMAGE ATTRIB", "LENGTH", "WIDTH", "SRC",
        "IMAGE TYPE", "PLAYER TYPE", "PLAYER ATTRIB", "LOOP", "MUTE", "ACTUATOR", "Y", "ACTUATOR TYPE", "STRUCT Y"
]

# Categories for RANGE, OPTION, BOOLEAN and other known categories
range_categories = [
    {"MIN", "MAX", "STEP", "OPERATION"},
    {"MIN", "MAX", "STEP", "OPERATION", "UNIT VAL"},
    {"MIN", "MAX", "STEP", "OPERATION", "UNIT VAL", "EXCEPT"}
]

def generate_option_categories(max_count):
    return [["String"] * count for count in range(2, max_count + 1)]

option_categories = generate_option_categories(10)

def extract_and_classify(data, path=""):
    classifications = []

    if isinstance(data, dict):
        for key, value in data.items():
            # Update the current path
            new_path = f"{path} -> {key}" if path else key
            if isinstance(value, (dict, list)):
                # Recursive call for nested structures
                classifications.extend(extract_and_classify(value, new_path))
            else:
                # Check if the key is part of valid_end_keywords and classify
                if key in valid_end_keywords:
                    category = classify_value(key, value)
                    classifications.append((new_path, value, category))
    elif isinstance(data, list):
        for item in data:
            classifications.extend(extract_and_classify(item, path))

    return classifications

def classify_value(key, value):
    if isinstance(value, str):
        return "String"
    elif isinstance(value, (int, float)):
        return "Numeric"
    elif key == "RANGE" and isinstance(value, list):
        # Classify RANGE values
        matching_category = next((category for category in range_categories if len(category) == len(value)), None)
        return str(matching_category) if matching_category else "Unknown RANGE Category"
    elif key == "OPTION" and isinstance(value, list):
        # Check for valid OPTION (string values expected)
        return "Valid OPTION" if all(isinstance(v, str) for v in value) else "Invalid OPTION (Non-string values)"
    elif key == "BOOLEAN" and isinstance(value, list):
        # Check for valid BOOLEAN (exactly two string values expected)
        return "[String, String]" if len(value) == 2 and all(isinstance(v, str) for v in value) else "Invalid BOOLEAN"
    elif isinstance(value, list):
        return "List of Values"
    else:
        return "Unknown Category"

# Sample JSON data
json_data = """
{
    "DEVICE" : {
        "NAME":"Air conditioner",
        "DOMAIN":"Smart home",
        "INSTALLATION":"Fixed"
    },
    "MODE" : {
        "COOL" : {
            "Temperature" : [16,"C"],
            "Fan" : ["medium"]
        },
        "DRY" : {
            "Temperature" : [25,"C"],
            "Fan" : ["high"]
        }                       
    },
    "CONTROL" : {
        "Temperature" : {
            "NUMERIC":{
                "RANGE":[14,30,0.5,"+","C"]
            }
        },                                 
        "Fan" : { 
            "STRING":{"OPTION":["low","medium","high", "very high"]}
        },      
        "Swing" : { 
            "BOOLEAN":["up","down"]
        },     
        "Power" : { 
            "BOOLEAN":["on","off"]
        } 
    }
}
"""

# Parse the JSON data
data = json.loads(json_data)

# Extract and classify data with the full path
classifications = extract_and_classify(data)

# Output the classifications with the full path
for path, value, category in classifications:
    print(f"{path} -> Value: {value} Category: {category}") 
