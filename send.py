import socket
import hashlib
from RPLCD.i2c import CharLCD
import subprocess

def compute_md5_of_data(message):
	message = message.encode('utf-8')
	
	md5_hash = hashlib.md5()
	md5_hash.update(message)
	
	return md5_hash.hexdigest()

def main():
    host = '0.0.0.0'  # Listen on all network interfaces
    port = 12345       # Port to listen on
    #lcd = CharLCD('PCF8574', 0x27)

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((host, port))
    server_socket.listen(1)
    print("Server listening on port", port)

    conn, addr = server_socket.accept()
    print("Connection from", addr)
    #lcd.clear()
    #lcd.cursor_pos = (0, 0)
    #lcd.write_string(f"Connected to:")
    #lcd.cursor_pos = (1, 0)
    #lcd.write_string(f"{addr}")
    
    

    # Receive message from client
    message = conn.recv(1024).decode('utf-8')
    #print("Received message:", message)
    
    message_list = message.split('@')
    mobile_publicKey = message_list[1].strip()
    #print(f'Public Key is {mobile_publicKey}')
    with open('/home/veman/Desktop/MobilePublicKey.txt', 'w') as file:
        file.write(mobile_publicKey)
    
    remaining_message_list = message_list[0].split(':')
    received_checksum = remaining_message_list[1].strip()
    
    total_message = remaining_message_list[0]
    
    #Checksum Validation
    generated_checksum = compute_md5_of_data(total_message)
    
    total_message_list = total_message.split(',')
    received_message = total_message_list[0]
    print(f'totalmessage {total_message_list}')
    print(received_message)
    received_ip = total_message_list[1].strip()
    print(f'IP address is {received_ip}')
    
    with open('/home/veman/Desktop/ip_address.txt', 'w') as file:
        file.write(f'Mobile IP is {received_ip}')

    if (received_message == "'send_file'") and (generated_checksum == received_checksum):
        print(f'Message received')
        
        #lcd.clear()
        #lcd.cursor_pos = (0, 0)
        #lcd.write_string("Message from User")
        #lcd.cursor_pos = (1, 0)
        #lcd.write_string(f"{received_message}")
         
        # Send file to client
        with open('/home/veman/Desktop/ac.json', 'rb') as file:
            data = file.read()
            conn.sendall(data)
            print(f'JSON file sent ...')
            
        with open('/home/veman/Desktop/ThingPublicKey.txt', 'rb') as file:
            data = file.read()
            conn.sendall(data)
            print(f'JSON file sent ...')    
            
        #lcd.clear()
        #lcd.cursor_pos = (0, 0)
        #lcd.write_string("Files Sent")
        #lcd.cursor_pos = (1, 0)
        #lcd.write_string(f"On Stand BY")
    
    conn.close()
    server_socket.close()
    subprocess.run(["sudo", "-E", "/home/veman/Desktop/py3.9testenv/bin/python3", "thing_mobile.py"], check=True)

if __name__ == '__main__':
    main()
