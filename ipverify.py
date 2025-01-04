import socket
import pyodbc
import time
import logging

# Configure logging
logging.basicConfig(filename='ip_update.log', level=logging.DEBUG, format='%(asctime)s %(message)s')

# Function to get the WLAN IP address
def get_wlan_ip():
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.settimeout(1)
            s.connect(('8.8.8.8', 80))
            ip_address = s.getsockname()[0]
        return ip_address
    except Exception as e:
        logging.error(f"Error obtaining WLAN IP address: {e}")
        return None

# Function to update the IP address in SQL Server
def update_ip_in_sql_server(ip_address):
    try:
        conn_str = (
            'DRIVER={ODBC Driver 18 for SQL Server};'
            'SERVER=10.203.5.193;'
            'DATABASE=IOT;'
            'UID=sa;'
            'PWD=Lokesh@0549;'
            'TrustServerCertificate=yes;'
            'Connection Timeout=10;'  # Increase the timeout
        )

        with pyodbc.connect(conn_str) as conn:
            cursor = conn.cursor()

            update_query = "UPDATE vendor SET thingIp = ? WHERE thingId = ?"
            condition_value = 'lokesh123'  # Adjust according to your logic

            cursor.execute(update_query, (ip_address, condition_value))
            conn.commit()
            logging.info(f"IP address {ip_address} updated successfully.")

    except pyodbc.Error as db_error:
        logging.error(f"Database error: {db_error}")
    except Exception as e:
        logging.error(f"Error updating IP address in SQL Server: {e}")

if __name__ == "__main__":
    while True:
        wlan_ip = get_wlan_ip()
        if wlan_ip:
            update_ip_in_sql_server(wlan_ip)
        else:
            logging.warning("Failed to obtain WLAN IP address.")
        
        # Wait for 5 seconds before updating again
        time.sleep(5)

