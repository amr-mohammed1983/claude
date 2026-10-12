# Tool reference

All tools take an optional `objectStore` (symbolic name; defaults to `FILENET_OBJECT_STORE`) except
`filenet_list_object_stores`. `ref` is an object ID (`{GUID}`) or a repository path (`/Folder/doc.pdf`).
`className` is a CE class symbolic name, so custom classes work everywhere. Results are JSON text;
failures are returned as MCP errors (`isError: true`).

Property values: strings, numbers, booleans and lists pass through; dates use `{"$date": "2024-01-31T00:00:00Z"}`.
Content arguments: `contentBase64` **or** `filePath` (local to the server; see `FILENET_FILE_ROOT`).

## Read-only tools

| Tool | Arguments | Description |
|------|-----------|-------------|
| `filenet_list_object_stores` | – | Symbolic and display names of the domain's object stores |
| `filenet_get_object` | `ref`*, `className` (Document), `properties[]` | All (or selected) properties of any object |
| `filenet_search` | `sql`*, `maxRows` (50, max 1000) | CE SQL query, e.g. `SELECT This, DocumentTitle FROM Document WHERE DocumentTitle LIKE 'Inv%'` |
| `filenet_describe_class` | `className`* | Property definitions: name, type, cardinality, required |
| `filenet_list_folder` | `ref`* | Sub-folders and contained documents |
| `filenet_download_content` | `ref`*, `elementIndex` (0), `saveTo` | Text types inline, other types base64, or written to `saveTo` |

## Write tools (hidden when `FILENET_READ_ONLY=true`)

| Tool | Arguments | Description |
|------|-----------|-------------|
| `filenet_create_document` | `className` (Document), `properties`, `folderPath`, `containmentName`, content args, `fileName`, `mimeType`, `checkin` (true) | Create (and optionally file) a document |
| `filenet_create_folder` | `parentPath`*, `name`*, `className` (Folder), `properties` | Create a folder |
| `filenet_update_object` | `ref`*, `properties`*, `className` (Document) | Update properties |
| `filenet_delete_object` | `ref`*, `className` (Document) | **Permanently delete** (all versions of a document) |
| `filenet_checkout` | `ref`* | Reserve a document; returns `reservationId` |
| `filenet_cancel_checkout` | `ref`* | Cancel the reservation |
| `filenet_checkin` | `ref`*, `properties`, content args, `majorVersion` (true) | Check in the reservation as a new version |

\* required

## Examples

```jsonc
// Find recent invoices
{"name": "filenet_search", "arguments": {"sql": "SELECT This, DocumentTitle, DateCreated FROM Document WHERE DocumentTitle LIKE 'Invoice%' ORDER BY DateCreated DESC", "maxRows": 10}}

// Upload a PDF into /Invoices
{"name": "filenet_create_document", "arguments": {"properties": {"DocumentTitle": "Invoice 42"}, "folderPath": "/Invoices", "filePath": "/data/inv42.pdf", "mimeType": "application/pdf"}}

// Update a property on a custom class
{"name": "filenet_update_object", "arguments": {"className": "Invoice", "ref": "{AAAA…}", "properties": {"Status": "Paid"}}}
```
