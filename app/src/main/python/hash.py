import hashlib

def generate_md5(input_data):
    md5_hash = hashlib.md5()
    md5_hash.update(input_data.encode('utf-8'))
    return md5_hash.hexdigest()

