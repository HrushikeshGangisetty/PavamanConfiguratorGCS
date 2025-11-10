import json
import urllib.request

# Fetch the JSON
url = "https://autotest.ardupilot.org/Parameters/ArduCopter/apm.pdef.json"
with urllib.request.urlopen(url) as response:
    data = json.loads(response.read())

# Print structure
print("Top level keys (first 5):", list(data.keys())[:5])
print("\n" + "="*80)

# Get first group
first_group_key = list(data.keys())[1]  # Skip first empty key
first_group = data[first_group_key]

print(f"\nFirst group: {first_group_key}")
print(f"Parameters in this group: {list(first_group.keys())[:3]}")

# Get first parameter
first_param_key = list(first_group.keys())[0]
first_param = first_group[first_param_key]

print(f"\n\nFirst parameter: {first_param_key}")
print("Fields in parameter:")
for key, value in first_param.items():
    if isinstance(value, str) and len(value) > 100:
        print(f"  {key}: {value[:100]}...")
    else:
        print(f"  {key}: {value}")

