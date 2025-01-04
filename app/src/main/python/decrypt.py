from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
import binascii

def decrypt_message(private_key_str, encrypted_message):
    # Load the private key from string
    private_key = serialization.load_pem_private_key(
        private_key_str.encode(),
        password=None,
        backend=default_backend()
    )

    # Convert the hex string back to bytes
    encrypted_message_bytes = binascii.unhexlify(encrypted_message)

    # Decrypt the message
    decrypted_message = private_key.decrypt(
        encrypted_message_bytes,
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )

    # Return the decrypted message as a string
    return decrypted_message.decode('utf-8')
