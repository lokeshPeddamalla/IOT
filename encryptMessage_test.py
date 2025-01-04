from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend

def encrypt_message(public_key, message):
	public_key = serialization.load_ssh_public_key(
		public_key,
		backend = default_backend()
	)
	encrypted_message = public_key.encrypt(
		message,
		padding.OAEP(
			mgf = padding.MGF1(algorithm=hashes.SHA256()),
			algorithm = hashes.SHA256(),
			label = None
		)
	)
	
	return encrypted_message
