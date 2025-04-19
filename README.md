# 🏠 House-To-House Server Systems

This project contains all the essential components and instructions for building a **secure cloud server system** for House-to-House infrastructure.

---

## 🌐 Live Deployment  
Access the main server interface here:  
🔗 [**Deployed on Vercel**](https://hthss-user-ui.vercel.app/)

---

## Interactive Circuit Preview

[![Preview of the Circuit](preview.png)](https://app.cirkitdesigner.com/project/9add52d5-1fe5-44a5-991c-8d9071a3059a?view=interactive_preview)

> Click the image above to open the interactive preview.


## ⚙️ Setup Instructions

### 🔸 Install Cloudflare Tunnel (Windows PowerShell)

Run the following command in **PowerShell** to download the latest Cloudflare tunnel binary:

```powershell
iwr -Uri https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe -OutFile cloudflared.exe
```

---

### 🔸 Running the UI Server

1. Place `ServerUI` and `run.bat` in the **same directory**.
2. Double-click `run.bat` to start the system.

---

