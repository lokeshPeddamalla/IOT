import socket

def send_message(message):
    HOST = "192.168.4.1"  # Replace with Raspberry Pi's static IP
    PORT = 12345            # Port to connect to

    try:
        # Create a socket
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.connect((HOST, PORT))

        # Send the message to the server
        client_socket.sendall(message.encode())

        # Receive the response from the server
        response = client_socket.recv(1024).decode()

        # Close the socket
        client_socket.close()

        return response  # Return the server's response

    except Exception as e:
        return f"Error: {e}"