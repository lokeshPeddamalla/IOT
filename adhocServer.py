import socket

# Server Configuration
HOST = "0.0.0.0"  # Listen on all interfaces
PORT = 12345       # Port to bind to

# Create a socket
server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server_socket.bind((HOST, PORT))
server_socket.listen(1)

print(f"Server is listening on {HOST}:{PORT}")

try:
    while True:
        # Wait for a client to connect
        client_socket, client_address = server_socket.accept()
        print(f"Connection established with {client_address}")

        # Receive data from the client
        data = client_socket.recv(1024).decode()
        if data:
            print(f"Received from client: {data}")

            # Send a response back to the client
            response = f"Message received: {data}"
            client_socket.send(response.encode())

        client_socket.close()

except KeyboardInterrupt:
    print("Shutting down the server...")
finally:
    server_socket.close()
