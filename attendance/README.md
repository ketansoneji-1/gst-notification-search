# Office Attendance

A small, self-hosted attendance and punctuality system for a professional office. It runs entirely on the office network; no cloud subscription and no extra software beyond Python on one PC.

## How it works

Two independent records are kept for every staff member, every day:

| Record | Source | Can staff alter it? |
|---|---|---|
| **PC record** | A background agent on the staff PC reports the first and last keyboard/mouse activity of the day. The time is stamped by the **server's** clock. | No. Changing the PC clock has no effect. Removing the agent shows up as "No PC record". |
| **Declared record** | The staff member signs in with a PIN on the attendance page (opened automatically at login) and enters the arrival and leaving time. | Entered once; locked after saving. The PC record is **never shown** to staff. |

The admin dashboard places both side by side and reports:

- **On time / Late / Absent** against the reporting time plus grace period (office default or per-staff).
- **Time mismatches** – declared arrival earlier, or declared leaving later, than the PC record beyond the tolerance.
- **Arrival not declared**, **Leaving not declared**, **No PC record**.
- **Monthly regularity ranking** – punctuality %, average arrival, total late minutes, average hours, mismatch count – with CSV export for Excel.
- **Admin corrections** (client visit, outdoor duty, etc.) with a mandatory remark; originals are preserved and every correction is logged in the audit trail.

### Client-wise work log (timesheet)

- **Staff** record each piece of work on the same page: client, type of work, time spent and optional details. They can see and remove only **their own** entries for today and the previous day; they have no access to any report or to other staff's entries.
- **Admin only** (password protected) – the **Work log** tab shows, for any period: client-wise hours broken down by type of work and staff, staff-wise hours logged against hours attended (from the attendance register), the detailed entries, filters by staff / client / type of work, and a CSV export for Excel (useful for billing and job costing).
- The **Clients** tab maintains the client list (paste many names at once) and the types of work. Standard types of work for a CA office are pre-loaded and can be renamed, added to or deactivated.

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
   - **Clients** – paste your client list and review the types of work.

To start the server automatically, place a shortcut to `start-server.bat` in the Startup folder (`Win+R` → `shell:startup`).

### 2. Each staff PC

1. Copy the `agent` folder to the PC (pen drive or shared folder).
2. Log in to Windows **as the staff member**, open Command Prompt in that folder and run the command shown on the admin **Settings** page:
   ```
   install-agent.bat http://192.168.1.10:8080 <agent-key>
   ```
3. The installer prints the Windows user name. Enter it against the staff member on the admin **Staff** page (unlinked PC users are also listed there for one-click linking).

No administrator rights are required. `uninstall-agent.bat` removes the agent.

### 3. Daily routine

- **Staff:** switch on the PC → attendance page opens → sign in with PIN → enter arrival time. During or at the end of the day, add work-log lines. Before leaving, enter leaving time (if forgotten, the page asks for it next morning).
- **Admin:** open `/admin` → **Daily** for today's live position, **Monthly** for the ranking and the CSV.

## Backup

All data is in the single file `attendance.db` in this folder. Copy it periodically (with the server stopped) to your regular backup location.

## Limitations to be aware of

- The PC record measures **PC usage, not physical presence**. A person may arrive and start work on the PC a few minutes later; set the grace period and tolerance accordingly, and use admin correction for outdoor duty.
- Each staff member needs an individual Windows login. Shared Windows accounts cannot be told apart.
- If the server PC is off, nothing is recorded for that period. Keep it running during office hours.
- The system is designed for a trusted office LAN (plain HTTP, PIN sign-in). Do not expose the port to the internet.
- The agent has been written for Windows PowerShell 5.1 (Windows 10/11). Test it on one PC before rolling out to all.

## Staff intimation

The agent records only the time of PC activity – no keystrokes, screens, websites or files. It is nevertheless advisable to inform staff in writing (appointment letter / office policy) that PC login activity is recorded for attendance purposes. Processing of employee personal data "for the purposes of employment" is a recognised legitimate use under Section 7(i) of the Digital Personal Data Protection Act, 2023; a transparent written policy remains good practice.

## Files

| File | Purpose |
|---|---|
| `server.py` | Server and database (Python standard library only) |
| `start-server.bat` | Starts the server on port 8080 |
| `web/staff.html` | Staff attendance page (`/`) |
| `web/admin.html` | Admin dashboard (`/admin`) |
| `agent/agent.ps1` | PC agent (runs hidden at login) |
| `agent/install-agent.bat`, `agent/uninstall-agent.bat` | Agent installer / remover |
