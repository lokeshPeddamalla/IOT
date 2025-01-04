import hashlib
import socket
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

def decrypt_message(private_key, encrypted_message):
    private_key = serialization.load_pem_private_key(
        private_key,
        password=None,
        backend=default_backend()
    )
    decrypted_message = private_key.decrypt(
        encrypted_message,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )
    return decrypted_message

# Load private key
with open('privateKey.txt', 'r') as private_key_file:
    private_key = private_key_file.read().encode()

server_ip = "0.0.0.0"  # Listen on all interfaces
server_port = 65432    # Port number (must match the sender)

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_socket:
    server_socket.bind((server_ip, server_port))
    server_socket.listen(1)
    
    conn, addr = server_socket.accept()
    
    while True:
        # Receive the payload
        data = conn.recv(4096).decode()
        if not data:
            print("Connection closed by client.")
            break

        encrypted_message_hex, original_checksum = data.split("::")
        encrypted_message = bytes.fromhex(encrypted_message_hex)

        # Generate MD5 checksum for the received encrypted message
        calculated_checksum = hashlib.md5(encrypted_message).hexdigest()

        # Verify checksum
        if calculated_checksum == original_checksum:
            print("Checksum verified.")
            # Decrypt the message
            decrypted_message = decrypt_message(private_key, encrypted_message)
            print("Decrypted Message:")
            print(decrypted_message.decode())
        else:
            print("Checksum verification failed. Data integrity compromised!")
