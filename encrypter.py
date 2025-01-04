import hashlib
import socket
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

def encrypt_message(public_key, message):
    public_key = serialization.load_ssh_public_key(
        public_key,
        backend=default_backend()
    )
    encrypted_message = public_key.encrypt(
        message,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )
    return encrypted_message

# Load public key
with open('publicKey.txt', 'r') as public_key_file:
    public_key = public_key_file.read().encode()

server_ip = "10.203.2.24"  # Replace with the IP address of the RPI
server_port = 65432        # Port number (ensure RPI is listening on the same port)

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client_socket:
    client_socket.connect((server_ip, server_port))
    print("Connected to RPI.")
    
    while True:
        # Get message input from user
        message = input("Enter the message to send (or 'exit' to quit): ").strip()
        if message.lower() == 'exit':
            print("Closing connection.")
            break

        # Encrypt the message
        encrypted_message = encrypt_message(public_key, message.encode())

        # Generate MD5 checksum of the encrypted message
        md5_hash = hashlib.md5(encrypted_message).hexdigest()

        # Send the encrypted message and checksum as a single payload
        payload = f"{encrypted_message.hex()}::{md5_hash}"
        client_socket.sendall(payload.encode())
        print("Message sent.")
