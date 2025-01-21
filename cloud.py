import threading
import time
from flask import Flask, request, jsonify
import pyodbc
import logging

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger()

# Local database configuration
local_db_config = {
    'driver': 'ODBC Driver 17 for SQL Server',
    'server': '10.203.5.79',
    'database': 'IOT',
    'username': 'sa',
    'password': 'Lokesh@0549'
}

lock = threading.Lock()

def connect_to_sql_server(config):
    try:
        conn_str = (
            f"DRIVER={{ODBC Driver 17 for SQL Server}};"
            f"SERVER={config['server']};"
            f"DATABASE={config['database']};"
            f"UID={config['username']};"
            f"PWD={config['password']}"
        )
        return pyodbc.connect(conn_str)
    except pyodbc.Error as e:
        logging.error(f"SQL Server connection error: {e}")
        return None

def connect_to_database(config):
    # Connect to SQL Server only
    return connect_to_sql_server(config)

def sync_ip_periodically():
    while True:
        connection_local = None
        try:
            # Connect to the local database
            connection_local = connect_to_database(local_db_config)
            if connection_local:
                # Fetch IP addresses from the vendor table
                query_vendor = "SELECT thingId, thingIp FROM [vendor]"
                cursor_local = connection_local.cursor()
                cursor_local.execute(query_vendor)
                records = cursor_local.fetchall()
                
                if records:
                    # Update IP addresses in the user table
                    query_update = "UPDATE [user] SET thingIp = ? WHERE thingId = ?"
                    cursor_local = connection_local.cursor()
                    for thing_id, vendor_ip in records:
                        cursor_local.execute(query_update, (vendor_ip, thing_id))
                    connection_local.commit()
                logger.info("IP addresses updated successfully in user table")
        
        except Exception as e:
            logger.error(f"Error during IP syncing: {e}")

        finally:
            if connection_local:
                connection_local.close()

        # Sleep for a specified interval before checking again
        time.sleep(5)

@app.route('/register_user', methods=['POST'])
def register_user():
    data = request.json
    username = data.get('username')
    email = data.get('email')
    mobile_num = data.get('mobileNum')
    thing_id = data.get('thingId')
    mobile_ip = data.get('mobileIp')
    # Get thingId from the request
    is_valid = False
    connection = None

    try:
        # Connect to the database
        connection = connect_to_database(local_db_config)
        cursor = connection.cursor()

        # Check if the thingId matches with the vendor table
        check_query = "SELECT COUNT(*) FROM [vendor] WHERE thingId = ?"
        cursor.execute(check_query, (thing_id,))
        matching_vendor = cursor.fetchone()[0]

        if matching_vendor == 0:
            # No matching thingId in the vendor table
            return jsonify({"error": "ThingId does not match with any entry in the vendor table."}), 400

        # Insert into the user table if thingId matches
        query = """
        INSERT INTO [user] (username, useremail, usermobilenum, thingId, mobileIp)
        VALUES (?, ?, ?, ?, ?)
        """
        cursor.execute(query, (username, email, mobile_num, thing_id, mobile_ip))
        
        if cursor.rowcount > 0:
            is_valid = True
        
        # Commit the transaction
        connection.commit()

    except Exception as e:
        return jsonify({"error": str(e)}), 500

    finally:
        if connection:
            connection.close()

    return jsonify({"registered": is_valid})

@app.route('/register_thing', methods=['POST'])
def register_thing():
    data = request.json
    thing_name = data.get('thingName')
    thing_id = data.get('thingId')
    thing_key = data.get('thingKey')
    is_valid = False
    connection = None

    try:
        connection = connect_to_database(local_db_config)
        cursor = connection.cursor()

        # Step 1: Check if the thingId exists in the user table
        check_user_query = "SELECT COUNT(*) FROM [user] WHERE thingId = ?"
        cursor.execute(check_user_query, (thing_id,))
        matching_user_count = cursor.fetchone()[0]

        if matching_user_count > 0:
            # Step 2: Check if the thingKey exists in the vendor table (ignoring thingId)
            check_vendor_query = "SELECT COUNT(*) FROM [vendor] WHERE thingKey = ?"
            cursor.execute(check_vendor_query, (thing_key,))
            matching_vendor_count = cursor.fetchone()[0]

            if matching_vendor_count > 0:
                # Step 3: If thingKey matches, update the thingName and thingKey in the user table
                update_user_query = """
                UPDATE [user]
                SET thingName = ?, thingKey = ?
                WHERE thingId = ?
                """
                cursor.execute(update_user_query, (thing_name, thing_key, thing_id))
                if cursor.rowcount > 0:
                    is_valid = True
            else:
                # If thingKey does not match in the vendor table
                return jsonify({"error": "ThingKey does not match in the vendor table."}), 400
        else:
            # If thingId does not exist in the user table
            return jsonify({"error": "ThingId does not exist in the user table."}), 400
        
        # Commit the transaction
        connection.commit()

    except Exception as e:
        return jsonify({"error": str(e)}), 500

    finally:
        if connection:
            connection.close()

    return jsonify({"registered": is_valid})

@app.route('/get_user_ip', methods=['GET'])
def get_user_ip():
    thing_id = request.args.get('thingId')
    if not thing_id:
        logging.error("thingId is missing in the request")
        return jsonify({"error": "thingId is required"}), 400

    connection = None
    try:
        logging.info(f"Received request for thingId: {thing_id}")
        connection = connect_to_database(local_db_config)
        query = "SELECT thingIp FROM [vendor] WHERE thingId = ?"
        cursor = connection.cursor()

        logging.debug(f"Executing query: {query} with thingId={thing_id}")
        cursor.execute(query, (thing_id,))
        results = cursor.fetchall()

        if results:
            # If you expect only one IP address, get the first result
            # If multiple IPs are expected, handle accordingly
            ip_address = results[0][0] if results else None
            if ip_address:
                logging.info(f"Extracted IP address: {ip_address}")
                return jsonify({"thingIp": ip_address})
            else:
                logging.warning(f"IP address is null for Thing ID {thing_id}")
                return jsonify({"error": "IP address is null"}), 404
        else:
            logging.warning(f"Thing ID {thing_id} not found")
            return jsonify({"error": "Thing ID not found"}), 404

    except Exception as e:
        logging.error(f"Error fetching IP: {e}")
        return jsonify({"error": str(e)}), 500
    finally:
        if connection:
            connection.close()

if __name__ == '__main__':
    # Start the IP syncing function in a separate thread
    sync_thread = threading.Thread(target=sync_ip_periodically, daemon=True)
    sync_thread.start()

    # Start the Flask application
    app.run(debug=True, host='0.0.0.0', port=5000)
