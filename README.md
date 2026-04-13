# GST Notification & Circular Search - India

A free, open-source, fully client-side search tool for all GST Notifications, Circulars, and Orders issued by CBIC (Central Board of Indirect Taxes and Customs).

**Live Demo:** `https://<your-username>.github.io/gst-notification-search/`

---

## Features

- **Full-text fuzzy search** across notification numbers, subjects, summaries, full text, keywords, and sections
- **Smart filters:** Document type (Notification/Circular), Tax type (CGST/IGST/UTGST/Cess), Category, Year, Date range, Section/Rule
- **Expandable cards** showing full text, summary, keywords, and section references
- **Search highlighting** with keyword match markers
- **Export to CSV** for filtered/searched results
- **Dark mode** toggle
- **Mobile responsive** design
- **No backend required** — runs entirely in the browser via GitHub Pages
- **Keyboard shortcut:** Press `/` to focus the search bar

---

## How to Deploy on GitHub Pages (Step-by-Step)

### Step 1: Create a GitHub Account (if you don't have one)
Go to [github.com](https://github.com) and sign up for a free account.

### Step 2: Create a New Repository
1. Click the **+** icon in the top-right corner → **New repository**
2. Repository name: `gst-notification-search`
3. Set it to **Public**
4. Check **Add a README file**
5. Click **Create repository**

### Step 3: Upload the Files
1. In your new repository, click **Add file** → **Upload files**
2. Drag and drop these files:
   - `index.html`
   - `data.json`
   - `README.md` (this file)
3. Write a commit message like "Initial upload of GST search app"
4. Click **Commit changes**

### Step 4: Enable GitHub Pages
1. Go to your repository's **Settings** tab
2. In the left sidebar, click **Pages**
3. Under **Source**, select **Deploy from a branch**
4. Select the **main** branch and **/ (root)** folder
5. Click **Save**
6. Wait 1-2 minutes, then visit: `https://<your-username>.github.io/gst-notification-search/`

Your GST search tool is now live and accessible to anyone!

---

## How to Add New Notifications / Circulars

### Option A: Edit Directly on GitHub (Easiest)
1. Navigate to `data.json` in your repository
2. Click the **pencil icon** (Edit this file)
3. Add a new entry at the end of the JSON array (before the closing `]`)
4. Follow this format:

```json
{
  "id": "NT-CGST-2025-001",
  "type": "Notification",
  "tax": "CGST",
  "number": "01/2025-Central Tax",
  "date": "2025-01-15",
  "effectiveDate": "2025-02-01",
  "subject": "Subject line of the notification",
  "category": "Rate",
  "sections": ["Section 9", "Schedule III"],
  "keywords": ["keyword1", "keyword2", "keyword3"],
  "summary": "Brief 2-3 line summary of the notification.",
  "fullText": "Complete text of the notification for full-text search.",
  "pdfUrl": "https://taxinformation.cbic.gov.in/view-pdf/XXXXXX/ENG/Notifications"
}
```

**How to find the PDF URL:**
1. Visit [taxinformation.cbic.gov.in](https://taxinformation.cbic.gov.in)
2. Navigate to the notification/circular
3. Right-click the PDF link and copy the URL
4. Paste it in the `pdfUrl` field

5. Commit the changes — the site updates automatically within minutes.

### Option B: Community Contributions via Pull Requests
1. Anyone can **fork** the repository
2. Edit `data.json` to add new entries
3. Submit a **Pull Request**
4. Repository owner reviews and merges
5. Site updates automatically

### Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| `id` | Yes | Unique identifier (e.g., NT-CGST-2024-016) |
| `type` | Yes | `Notification`, `Circular`, or `Order` |
| `tax` | Yes | `CGST`, `IGST`, `UTGST`, or `Cess` |
| `number` | Yes | Official number (e.g., 16/2024-Central Tax) |
| `date` | Yes | Date of issue in YYYY-MM-DD format |
| `effectiveDate` | No | Effective date in YYYY-MM-DD format |
| `subject` | Yes | Official subject line |
| `category` | Yes | Category (see list below) |
| `sections` | No | Array of sections/rules referenced |
| `keywords` | No | Array of search keywords |
| `summary` | Yes | Brief summary (2-3 sentences) |
| `fullText` | Yes | Full text for search indexing |
| `pdfUrl` | No | Direct URL to official PDF on CBIC portal |

### Categories Used
`Commencement`, `Rules`, `Rules Amendment`, `Rate`, `Rate Clarification`, `Exemption`, `Reverse Charge`, `Composition`, `Registration`, `Return Filing`, `Input Tax Credit`, `E-Way Bill`, `E-Invoice`, `TDS`, `Refund`, `Interest`, `Amendment`, `Amnesty`, `Adjudication`, `Valuation`, `Export`, `Place of Supply`, `SEZ`, `IBC`, `Administration`, `Clarification`, `COVID Relief`

---

## Data Sources

The notification/circular data should be sourced from:
- **CBIC GST Portal:** https://cbic-gst.gov.in
- **Tax Information Network:** https://taxinformation.cbic.gov.in
- **Official Gazette:** https://egazette.gov.in

---

## Technology Stack

- **HTML5 + CSS3 + Vanilla JavaScript** (no framework dependencies)
- **Fuse.js** (v7.0.0) for fuzzy search — loaded from CDN
- **Google Fonts (Inter)** for typography
- **GitHub Pages** for free hosting

---

## License

This project is open source and available under the [MIT License](LICENSE).

---

## Disclaimer

This is an **unofficial tool** created for the convenience of tax professionals. Always verify information with official CBIC notifications published in the Gazette of India. The maintainers are not responsible for any errors or omissions in the data.

---

## Contributing

Contributions are welcome! The most helpful contribution is adding new notifications and circulars to `data.json`. Please ensure:
1. The data is accurate and sourced from official CBIC publications
2. The JSON format is valid (use a JSON validator)
3. Keywords are relevant and helpful for search
4. Summary accurately reflects the notification content

---

Built with care for the Indian CA & Tax Professional Community.
