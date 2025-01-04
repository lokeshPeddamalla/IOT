# verify_checksum.py
import hashlib
import sys

def generate_md5(input_data):
    md5_hash = hashlib.md5()
    md5_hash.update(input_data.encode('utf-8'))
    return md5_hash.hexdigest()

def verify_checksum(message_with_checksum):
    message, provided_checksum = message_with_checksum.split(':')
    calculated_checksum = generate_md5(message)
    return provided_checksum == calculated_checksum

if __name__ == "__main__":
    message_with_checksum = sys.argv[1]  # Read message:checksum input
    is_valid = verify_checksum(message_with_checksum)
    print("True" if is_valid else "False")  # Output True or False
