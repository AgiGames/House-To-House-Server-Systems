import serial
import os
import datetime

# Set your COM port (check in Device Manager)
COM_PORT = "COM7"  # Change as per your system (e.g., /dev/ttyUSB0 on Linux)
BAUD_RATE = 9600
ROOT_DIR = r"D:\Education\PythonProjects\MainServer\SubServerData"

def receive_files():
    ser = serial.Serial(COM_PORT, BAUD_RATE, timeout=2)
    save_dir = os.path.join(ROOT_DIR, datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S"))
    os.makedirs(save_dir, exist_ok=True)

    current_file = None
    while True:
        line = ser.readline().decode(errors="ignore").strip()
        
        if line == "TRANSFER_START":
            print("Starting file transfer...")
        
        elif line.startswith("FILE_START:"):
            filename = line.split(":", 1)[1].strip()
            current_file = open(os.path.join(save_dir, filename), "wb")
            print(f"Receiving {filename}...")
        
        elif line == "FILE_END":
            if current_file:
                current_file.close()
                current_file = None
                print("File received successfully.")

        elif line == "TRANSFER_END":
            print("File transfer completed.")
            break

        elif current_file:
            current_file.write(line.encode())  # Write file data

    ser.close()

if __name__ == "__main__":
    receive_files()
