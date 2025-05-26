**CheckDLStore.groovy**

This script checks for missing physical files in the Liferay Document Library corresponding to entries in the DLFileVersion table. It is designed to be executed via "Server Administration" → "Script" console in DXP 2024.q2 >=JDK11

**What It Does**
Iterates over all DLFileEntry and DLFileVersion records.

Verifies if the expected file exists in the DLStore.

Logs missing files with detailed metadata in a format for easy export.

Optionally, deletes orphaned DLFileVersion and DLFileEntry records (if safeMode is set to false).

**Output**
When a missing file is detected, the script prints lines with the following fields:

```
Missing file detected:
  fileEntryId: 32743
  fileVersionId: 32744
  version: 1.0
  storeUuid: e5d15e05-a18d-4a9b-b99c-435998464c17
  fileTitle: Test File
  fileName: Test File.png
  mimeType: image/png
  folderId: 32741, folderPath: ROOT > test
  groupId: 20117, siteName: Liferay DXP
  companyId: 36048738074240, virtualHost: localhost
  Expected download URL: /documents/20117/32741/101/e5d15e05-a18d-4a9b-b99c-435998464c17
  ctCollectionId: 0
```

**Configuration**
safeMode = true: Read-only mode (default). No records are deleted.

safeMode = false: Automatically deletes orphaned records from:

DLFileVersion

DLFileEntry (if it has no remaining versions)

Related ExpandoRow and WorkflowInstanceLink entries

**How to Run**
Open Liferay's Control Panel → Server Administration → Script.


Paste the script and execute.

💡 IMPORTANT!!!: Start in safeMode = true to audit missing files before enabling deletion.

**Requirements**
Liferay DXP (version with DLFileEntry, DLFileVersion, DLStoreUtil, etc.)
JDK 11 or greater
