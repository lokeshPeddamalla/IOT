from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend
import binascii

def encrypt_message(public_key_str, message):
    # Load the public key from string
    public_key = serialization.load_pem_public_key(
        public_key_str.encode(),
        backend=default_backend()
    )

    # Encrypt the message
    encrypted_message = public_key.encrypt(
        message.encode(),
        padding.OAEP(
            mgf=padding.MGF1(algorithm=hashes.SHA256()),
            algorithm=hashes.SHA256(),
            label=None
        )
    )

    # Return the encrypted message in hexadecimal format
    return binascii.hexlify(encrypted_message).decode('utf-8')
