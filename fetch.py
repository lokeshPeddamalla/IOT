# JSON data structure
data_structure = {
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

# List of keywords
keywords = {
   "S", "START", "OPTIONAL", "SENSOR", "ACTUATOR", "DEVICE", "OEM", "MODE", "LOCATION", "D", "NAME", "DOMAIN",
    "WARRANTY", "INSTALLATION", "SW VERSION", "HW VERSION", "O", "SOCKET", "M", "NAME VAL", "Z", "DATA",
    "ACTUATOR INFO", "VALUE", "UNIT VAL", "AREA", "X", "DESC", "SENSOR TYPE", "STRUCT X", "BATTERY", "GRAPH",
    "DATA TYPE", "PIE", "BAR", "GRAPH TYPE", "CORD1", "CORD2", "BOOLEAN", "NUMERIC", "STRING", "IMAGE", "AUDIO",
    "VIDEO", "MAP", "COLOR", "DATE", "TIME", "RANGE", "TUPLE", "UNIT", "R", "RVAL", "MIN", "MAX", "STEP",
    "UNIT VAL", "OPERATION", "EXCEPT", "TVAL", "OPTION", "OVAL", "SIZE", "IMAGE ATTRIB", "LENGTH", "WIDTH", "SRC",
    "IMAGE TYPE", "PLAYER TYPE", "PLAYER ATTRIB", "LOOP", "MUTE", "ACTUATOR", "Y", "ACTUATOR TYPE", "STRUCT Y"
}

# Function to draw the tree with numbering
def draw_tree_with_keywords_labeled(data, keywords, indent=0, level=1):
    tree = ""
    for key, value in data.items():
        if key in keywords:  # If the key matches the keywords
            tree += "  " * indent + f"{key} ({level}):\n"
            if isinstance(value, dict):
                tree += draw_tree_with_keywords_labeled(value, keywords, indent + 1, level + 1)
            elif isinstance(value, list):
                tree += "  " * (indent + 1) + f"{value}\n"
            else:
                tree += "  " * (indent + 1) + f"{value}\n"
        else:  # Non-keywords are included but not labeled
            if isinstance(value, dict):
                tree += "  " * indent + f"{key}:\n"
                tree += draw_tree_with_keywords_labeled(value, keywords, indent + 1, level)
            elif isinstance(value, list):
                tree += "  " * indent + f"{key}: {value}\n"
            else:
                tree += "  " * indent + f"{key}: {value}\n"
    return tree

# Function to find the immediate parent of a given keyword
def find_parent_of_keyword(data, target_keyword, keywords, parent=None):
    for key, value in data.items():
        if key == target_keyword:  # Found the target keyword
            return parent  # Return its immediate parent
        if isinstance(value, dict):
            # Recursively search in nested dictionaries
            result = find_parent_of_keyword(value, target_keyword, keywords, parent=key if key not in keywords else parent)
            if result:
                return result
        elif isinstance(value, list):
            # If a list, continue looking for the keyword
            continue
    return None  # If not found, return None

# Generate the labeled tree
filtered_tree_with_labels = draw_tree_with_keywords_labeled(data_structure, keywords)

# Functionality to print the tree and find the parent of a keyword
print("Filtered Tree with Keywords Labeled:")
print(filtered_tree_with_labels)

# Example usage: Find the parent of a given keyword
target_keyword = input("Enter the keyword to find its non-keyword parent: ").strip()
parent = find_parent_of_keyword(data_structure, target_keyword, keywords)
if parent:
    print(parent)
