# Office Attendance

A small, self-hosted attendance and punctuality system for a professional office. It runs entirely on the office network; no cloud subscription and no extra software beyond Python on one PC.

## How it works

Two independent records are kept for every staff member, every day:

| Record | Source | Can staff alter it? |
|---|---|---|
| **PC record** | A background agent in the staff member's Windows / RDP session reports the first and last keyboard/mouse activity of the day. The time is stamped by the **server's** clock. | No. Changing the PC clock has no effect. Removing the agent shows up as "No PC record". |
| **Declared record** | The staff member signs in with a PIN on the attendance page (opened automatically at login) and enters the arrival and leaving time. | Entered once; locked after saving. The PC record is **never shown** to staff. |

The admin dashboard places both side by side and reports:

- **On time / Late / Absent** against the reporting time plus grace period (office default or per-staff).
- **Time mismatches** – declared arrival earlier, or declared leaving later, than the PC record beyond the tolerance.
- **Arrival not declared**, **No PC record**.
- **Leaving auto-punched** – if a staff member leaves without declaring the leaving time, the last PC activity of that day (accurate to about five minutes) is punched as the leaving time and marked as such.
- **Monthly regularity ranking** – punctuality %, average arrival, total late minutes, average hours, mismatch count – with CSV export for Excel.
- **Admin corrections** (client visit, outdoor duty, etc.) with a mandatory remark; originals are preserved and every correction is logged in the audit trail.

### Automatic client-wise work log

Staff enter nothing. While a session is in use, the agent notes every 10 seconds which program and window title is in front (for example `Shree Traders GSTR1 Oct.xlsx - Excel`). The server then works out:

- **Client** – the window title is searched for each client's name and its keywords (short forms, Tally company name, usual file-name spellings).
- **Type of work** – the program name and title are searched for the type-of-work keywords (`tally` → Accounting, `gst` / `gstr1` → GST Returns, `3cd` → Tax Audit, `26q` / `traces` → TDS, and so on). All keywords are editable.

For a file opened by double-click, the agent also records its full path, so a client folder name (`E:\Clients\Shree Traders\...`) identifies the client even when the file name does not. Whole words only; the longest match wins. Matching is done when the report is opened, so adding a keyword corrects past periods as well.

The **Work log** tab (admin password only – staff have no access to it at all) shows for any period: client-wise hours by type of work and staff, each staff member's active hours against attended hours, the windows where no client could be identified (so you can add keywords), the full window-level detail, filters, and a CSV export for billing or job costing. The **Clients** tab maintains the client list (paste many names at once) and the keywords.

The PC record prevails for status and hours; the declared time is used only where no PC record exists; an admin correction overrides both.

## Setup (about 15 minutes)

### 1. Server PC (one always-on office PC)

1. Install Python 3.9 or later from python.org (tick **Add Python to PATH**).
2. Copy this `attendance` folder to the PC, e.g. `C:\attendance`.
3. Double-click `start-server.bat`. The window shows the staff and admin addresses, e.g. `http://192.168.1.10:8080/`.
4. When Windows Firewall prompts, allow access on **Private networks**.
5. Give this PC a fixed IP address (router DHCP reservation), so the address never changes.
6. Open `http://<server-ip>:8080/admin`, create the admin password, then:
   - **Settings** – firm name, reporting time, grace period, tolerance, weekly off, holidays.
   - **Staff** – add each staff member with a starting PIN (staff can change it later).
   - **Clients** – paste your client list, add keywords for each client and review the type-of-work keywords.

To start the server automatically, place a shortcut to `start-server.bat` in the Startup folder (`Win+R` → `shell:startup`).

### 2. Agent – RDP server (all staff work in Remote Desktop sessions)

1. Run the attendance server (step 1) on the RDP server itself.
2. In the `agent` folder, double-click **`install-all-users.bat`** and accept the administrator prompt. It reads the agent key itself and installs the agent for every Windows user of the server. (Manual alternative: Command Prompt as administrator, `install-agent.bat http://127.0.0.1:8080 <agent-key> all`.)
3. Staff who are connected must disconnect and reconnect once (or lock and unlock). From then on the agent starts in every session at sign-in, and also whenever a user reconnects to or unlocks a session that was left open.
4. On the admin **Staff** page, link each Windows user name of the server to the staff member (users already sending data are listed there for one-click linking).

Each staff member must have an individual Windows account on the server. A disconnected RDP session produces no activity, so it counts neither as attendance nor as work.

For a staff member working on a separate PC instead, copy the `agent` folder there and run `install-agent.bat http://<server-ip>:8080 <agent-key>` while logged in as that person (no administrator rights needed). `uninstall-agent.bat` removes the agent.

### 3. Daily routine

- **Staff:** switch on the PC → attendance page opens → sign in with PIN → enter arrival time. Before leaving, enter leaving time (if forgotten, the page asks for it next morning).
- **Admin:** open `/admin` → **Daily** for today's live position, **Monthly** for the ranking and the CSV, **Work log** for client-wise time. In the first weeks, review "client not identified" windows and add keywords.

## Troubleshooting: PC columns are empty / no automatic records

The admin **Daily** tab shows a yellow notice, and **Settings → Agent status** lists every Windows user whose session has sent a signal.

1. **Nobody listed under Agent status** – the agent is not installed or not running. On the RDP server, open Command Prompt with *Run as administrator* and run the install command from the Settings page **ending in `all`** (without `all`, it installs only for the user who ran it), or simply double-click `install-all-users.bat`. Then each user reconnects or locks and unlocks once.
2. **Users listed but "Not linked"** – open the **Staff** tab and enter that Windows user name against the staff member. Data already received appears immediately.
3. **Still nothing for a user** – sign in as that user (in their RDP session) and double-click `C:\ProgramData\OfficeAttendance\check-agent.bat` (or the copy in the `agent` folder). It prints OK / FAIL for each step:
   - *Agent files installed / Startup entry present* FAIL → run the installer again as administrator with `all`.
   - *Agent running in this session* FAIL while the test signal is OK → hidden PowerShell started from the Startup folder is being blocked, usually by antivirus or group policy. Allow `C:\ProgramData\OfficeAttendance\agent.ps1` in the antivirus, then sign out and in.
   - *Server reachable* FAIL → `start-server.bat` is not running, or the address is wrong. On the server itself use `http://127.0.0.1:8080` rather than `localhost`.
   - *Test signal accepted* FAIL with "Bad agent key" → reinstall with the key shown on the Settings page.
   - *Activity detection* FAIL → PowerShell is restricted on this machine; attendance is still recorded but not idle time or window titles.
4. The agent writes its own problems to `%LOCALAPPDATA%\OfficeAttendance\agent.log` for each user; `check-agent.bat` shows the last lines.

## Backup

All data is in the single file `attendance.db` in this folder. Copy it periodically (with the server stopped) to your regular backup location.

## Limitations to be aware of

- The PC record measures **PC usage, not physical presence**. A person may arrive and start work on the PC a few minutes later; set the grace period and tolerance accordingly, and use admin correction for outdoor duty.
- Each staff member needs an individual Windows login. Shared Windows accounts cannot be told apart.
- If the server PC is off, nothing is recorded for that period. Keep it running during office hours.
- The system is designed for a trusted office LAN (plain HTTP, PIN sign-in). Do not expose the port to the internet.
- **Client identification depends on the client's name or keyword appearing in the window title.** It works well for Excel, Word and PDF files and folders named after the client. Government portals (GST, Income Tax, TRACES, MCA) do not show the taxpayer's name in the browser title, and Tally may not show the company name in its title bar; such time is still classified by type of work but shown as "Client not identified". A consistent file and folder naming convention improves coverage considerably.
- Only the window in front is counted, and only while there is keyboard/mouse activity. Reading a long document without touching the mouse for over two minutes is not counted.
- A staff member can end the agent in their own session; this shows up as missing PC record and low active hours for that day.
- The agent has been written for Windows PowerShell 5.1 (Windows 10/11) and has **not yet been run on a real Windows machine**. Test it with one user before relying on it.

## Staff intimation

The agent records the time of activity and the program name and window title in front – which includes file names and browser page titles, and therefore any personal browsing done on the office server. It does not record keystrokes, screen images, passwords or file contents. Staff should be informed in writing (appointment letter / office policy) that session activity and window titles on office systems are recorded for attendance and work-allocation purposes. Processing of employee personal data "for the purposes of employment" is a recognised legitimate use under Section 7(i) of the Digital Personal Data Protection Act, 2023; a transparent written policy remains good practice.

## Files

| File | Purpose |
|---|---|
| `server.py` | Server and database (Python standard library only) |
| `start-server.bat` | Starts the server on port 8080 |
| `web/staff.html` | Staff attendance page (`/`) |
| `web/admin.html` | Admin dashboard (`/admin`) |
| `agent/agent.ps1` | Session agent (runs hidden at sign-in) |
| `agent/install-agent.bat`, `agent/uninstall-agent.bat` | Agent installer / remover |
| `agent/install-all-users.bat` | One-click install for all users of the RDP server |
| `agent/register-task.ps1` | Used by the installer: scheduled task that starts the agent on sign-in, reconnect and unlock |
| `agent/check-agent.bat`, `agent/check-agent.ps1` | Diagnostic: shows which step fails in a session |
