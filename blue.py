import bluetooth
import os
import subprocess
import hashlib

def compute_md5_of_data(message):
	message = message.encode('utf-8')
	
	md5_hash = hashlib.md5()
	md5_hash.update(message)
	
	return md5_hash.hexdigest()

# Define the UUID for SPP
uuid = "00001101-0000-1000-8000-00805F9B34FB"

# Create a socket
server_socket = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
server_socket.bind(("", bluetooth.PORT_ANY))
server_socket.listen(10)

# Advertise the service
bluetooth.advertise_service(
    server_socket, "SampleServer",
    service_id=uuid,
    service_classes=[uuid, bluetooth.SERIAL_PORT_CLASS],
    profiles=[bluetooth.SERIAL_PORT_PROFILE]
)

print("Waiting for connection on RFCOMM channel...")
client_socket, client_info = server_socket.accept()
print(f"Accepted connection from {client_info}")

# Read the credentials from credentials.txt
def load_credentials():
    credentials = {}
    try:
        with open('credentials.txt', 'r') as file:
            for line in file:
                key, value = line.strip().split(': ', 1)
                credentials[key] = value
    except FileNotFoundError:
        print("credentials.txt file not found")
    return credentials

# Function to validate the received data
def validate_received_data(data):
    credentials = load_credentials()
    received_message = data

    try:
        # Extract thingId and thingKey from the message
        parts = received_message.split('thingId: ')[1].split('thingKey: ')
        received_thing_id = parts[0].strip()
        received_thing_key = parts[1].strip()

        if (credentials.get('thingId') == received_thing_id and
                credentials.get('thingKey') == received_thing_key):
            print("Validated successfully")
            return True
        else:
            print("Validation failed")
            return False
    except IndexError:
        print("Invalid message format")
        return False

def send_file(file_path):
    try:
        # Send the complete file
        with open(file_path, 'rb') as file:
            # Read the entire file
            file_data = file.read()
            client_socket.sendall(file_data)
        print(f"File {file_path} sent successfully")
    except Exception as e:
        print(f"Error sending file: {e}")

# Function to run the temperature.py script
def run_temperature_script():
    try:
        subprocess.run(["sudo", "-E", "/home/veman/Desktop/py3.9testenv/bin/python3", "thing_bluetooth.py"], check=True)
        print("temperature.py script executed successfully")
    except subprocess.CalledProcessError as e:
        print(f"Error running temperature.py: {e}")

try:
    while True:
        checksumdata = client_socket.recv(1024).decode('utf-8')
        if not checksumdata:
            break
        print(f"Received: {checksumdata}")
        
        checksumdata_list = checksumdata.split('%')
        publicKeydata = checksumdata_list[1].strip()
        print(f'publickey received is {publicKeydata}')
        checksumIPdata = checksumdata_list[0].strip()
        checksumIPdata_list = checksumIPdata.split('+')
        data = checksumIPdata_list[0].strip()
        recieved_checksum = checksumIPdata_list[1].strip()
        generated_checksum = compute_md5_of_data(data)
        
        with open('/home/veman/Desktop/MobilePublicKey.txt', 'w') as file:
            file.write(publicKeydata)

        if validate_received_data(data) and (recieved_checksum == generated_checksum):
            print(f'Checksum Verified')
            # Send ac.json file upon successful validation
            file_path = '/home/veman/Desktop/ac.json'
            if os.path.exists(file_path):
                send_file(file_path)
            else:
                print("ac.json file not found")
                
            file_path = '/home/veman/Desktop/ThingPublicKey.txt'
            if os.path.exists(file_path):
                send_file(file_path)
                run_temperature_script()  # Run the temperature.py script after sending the file
            else:
                print("ac.json file not found")
                
            
except Exception as e:
    print(f"Error: {e}")
finally:
    client_socket.close()
    server_socket.close()  
