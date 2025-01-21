import socket
import select
import hashlib
from decryptMessage_test import decrypt_message
from encryptMessage_test import encrypt_message

def compute_md5_of_data(message):
	message = message.encode('utf-8')
	
	md5_hash = hashlib.md5()
	md5_hash.update(message)
	
	return md5_hash.hexdigest()

server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

host = "0.0.0.0"
port = 12345

mobile_ip = []
with open('ip_address.txt', 'r') as file:
	content = file.readlines()
	for l in content:
		l = l.split(' ')
		l = l[-1]
		mobile_ip.append(l.strip())

server_socket.bind((host, port))

server_socket.listen(5)
print(f'Thing Listening on {host}:{port}')

while True:
	client_socket, addr = server_socket.accept()
	client_socket.setblocking(0)
	print(f'Got connection from {addr}')
	
	#print(f'{mobile_ip}')
	
	if addr[0] in mobile_ip:
		print(f'verifying match with {mobile_ip}')
		
		count = 0
		while True:
			try:
				read_to_read, _, _ = select.select([client_socket], [], [], 5)
				encrypted_message = client_socket.recv(1024).decode('utf-8')
				#print(f'Received data: {encrypted_message}')
				
				#Decrypted the message
				privateKey_file = open('ThingPrivateKey.txt', 'r')
				private_key = privateKey_file.read().encode()
		
				encrypted_message = bytes.fromhex(encrypted_message)
				decrypted_message = decrypt_message(private_key, encrypted_message)
				print(f'decrypted_Message: {decrypted_message}')
		
				#Split the received message to data and checksum
				md5_combined_data = decrypted_message.decode('utf-8')
				md5_combined_data = md5_combined_data.split(',')
				#print(f'data split {md5_combined_data}')
				data = md5_combined_data[0].strip()
				data_split = data.split('+')
			
				data_message = data_split[0].strip()
				data_message = data_message.split(':')
				data_message = data_message[1].strip()
			
				data_ip = data_split[1].strip()
				data_ip = data_ip.split(':')
				data_ip = data_ip[1].strip()
			
				print(data)
				md5_data = md5_combined_data[1]
		
				#Verify the checksum of the received data
				md5_verify = compute_md5_of_data(data)
				print(f'{md5_verify} and {md5_data}')
			
				if (md5_verify == md5_data):
					count = 0
					#Send the response and checksum
					thing_ip = ''
					with open('Thing_IP.txt', 'r') as file:
						content = file.read()
						content = content.split(' ')
						content = content[-1]
						thing_ip = content.strip()
		
					response = 'Hello Mobile'
					total_message = f'Message:{response}+IP:{thing_ip}'
					response_checksum = compute_md5_of_data(total_message)
					responding_message = f'{total_message},{response_checksum}'.encode('utf-8')
		
					publicKey_file = open('MobilePublicKey.txt', 'r')
					public_key = publicKey_file.read().encode()
					encrypted_response = encrypt_message(public_key, responding_message)
					encrypted_response = encrypted_response.hex()
					
					client_socket.send(encrypted_response.encode('utf-8'))
				else:
					count = count + 1
			except BlockingIOError:
				count = count + 1
				if count < 3:
					print('No Message Received ...')
					
					thing_ip = ''
					with open('Thing_IP.txt', 'r') as file:
						content = file.read()
						content = content.split(' ')
						content = content[-1]
						thing_ip = content.strip()
						
					response = 'Hello Mobile'
					total_message = f'Message:{response}+IP:{thing_ip}'
					response_checksum = compute_md5_of_data(total_message)
					responding_message = f'{total_message},{response_checksum}'.encode('utf-8')
		
					publicKey_file = open('MobilePublicKey.txt', 'r')
					public_key = publicKey_file.read().encode()
					encrypted_response = encrypt_message(public_key, responding_message)
					encrypted_response = encrypted_response.hex()
					
					client_socket.send(encrypted_response.encode('utf-8'))
				else:
					client_socket.close()
					print(f'No Acknowledgement Received on IP switching to Bluetooth')
					break
					
	
		client_socket.close()
		print(f'Trying Bluetooth Communication')
		#Bluetooth Communication
