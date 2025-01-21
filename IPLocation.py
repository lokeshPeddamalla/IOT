import requests

ip_address = "2401:4900:55b6:2a90:71a5:9645:7f9f:b8da"
response = requests.get(f"https://ipinfo.io/{ip_address}/json")
data = response.json()
print(data)
