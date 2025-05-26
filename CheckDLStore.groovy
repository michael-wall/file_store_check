import com.liferay.document.library.kernel.model.DLFileEntry
import com.liferay.document.library.kernel.model.DLFolderConstants
import com.liferay.document.library.kernel.service.DLFileEntryLocalServiceUtil
import com.liferay.document.library.kernel.service.DLFileVersionLocalServiceUtil
import com.liferay.document.library.kernel.service.DLFolderLocalServiceUtil
import com.liferay.document.library.kernel.store.DLStoreUtil
import com.liferay.expando.kernel.service.ExpandoRowLocalServiceUtil
import com.liferay.portal.kernel.change.tracking.CTCollectionThreadLocal
import com.liferay.portal.kernel.dao.jdbc.DataAccess
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil
import com.liferay.portal.kernel.service.GroupLocalServiceUtil
import com.liferay.portal.kernel.service.WorkflowInstanceLinkLocalServiceUtil
import com.liferay.portal.kernel.util.PortalUtil
import com.liferay.portal.kernel.util.Validator
import com.liferay.petra.lang.SafeCloseable

import java.io.PrintWriter
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Locale

class CheckDLStore {

    PrintWriter out
    boolean safeMode = true
    Locale locale = Locale.US

    CheckDLStore(PrintWriter out) {
        this.out = out
    }

    void execute() {
        Connection con = null
        PreparedStatement ps = null
        ResultSet rs = null

        try {
            con = DataAccess.getConnection()

            String sql = """
                SELECT f.companyId, f.fileEntryId, f.repositoryId, f.folderId, f.name,
                       v.version, v.fileVersionId, f.groupId, v.storeUuid, f.ctCollectionId
                FROM DLFileEntry f
                JOIN DLFileVersion v
                  ON f.fileEntryId = v.fileEntryId
                 AND f.ctCollectionId = v.ctCollectionId
            """

            sql = PortalUtil.transformSQL(sql)

            ps = con.prepareStatement(sql)
            rs = ps.executeQuery()

            int rows = 0
            int missingDLFileVersions = 0
            int missingDLFileEntries = 0

            out.println("----")
            if (safeMode)
                out.println("Running on read-only mode")
            out.println("Checking DLFileVersions missing physical file in DLStore...")
            out.println("----")

            while (rs.next()) {
                long companyId = rs.getLong(1)
                long fileEntryId = rs.getLong(2)
                long repositoryId = rs.getLong(3)
                long folderId = rs.getLong(4)
                String name = rs.getString(5)
                String version = rs.getString(6)
                long fileVersionId = rs.getLong(7)
                long groupId = rs.getLong(8)
                String storeUuid = rs.getString(9)
                long ctCollectionId = rs.getLong(10)

                SafeCloseable sc = CTCollectionThreadLocal.setCTCollectionIdWithSafeCloseable(ctCollectionId)

                try {
                    long dataRepositoryId = DLFolderConstants.getDataRepositoryId(repositoryId, folderId)

                    String storeFileName = Validator.isNotNull(storeUuid) ?
                        "${version}~${storeUuid}" : version

                    boolean exists = DLStoreUtil.hasFile(companyId, dataRepositoryId, name, storeFileName)

                    if (!exists) {
                        DLFileEntry fileEntry = DLFileEntryLocalServiceUtil.fetchDLFileEntry(fileEntryId)

                        String fileTitle = fileEntry?.getTitle()
                        String fileName = fileEntry?.getFileName()
                        String mimeType = fileEntry?.getMimeType()

                        String folderPath = getFullFolderPath(folderId)
                        String groupName = GroupLocalServiceUtil.fetchGroup(groupId)?.getDescriptiveName(locale)
                        String virtualHostname = CompanyLocalServiceUtil.getCompany(companyId)?.getVirtualHostname()

                        String fullURL = "/documents/${groupId}/${folderId}/${name}/${storeUuid}"
                        out.println("Missing file detected:")
                        out.println("  fileEntryId: ${fileEntryId}")
                        out.println("  fileVersionId: ${fileVersionId}")
                        out.println("  version: ${version}")
                        out.println("  storeUuid: ${storeUuid}")
                        out.println("  fileTitle: ${fileTitle}")
                        out.println("  fileName: ${fileName}")
                        out.println("  mimeType: ${mimeType}")
                        out.println("  folderId: ${folderId}, folderPath: ${folderPath}")
                        out.println("  groupId: ${groupId}, siteName: ${groupName}")
                        out.println("  companyId: ${companyId}, virtualHost: ${virtualHostname}")
                        out.println("  Expected download URL: ${fullURL}")
                        out.println("  ctCollectionId: ${ctCollectionId}")
                        out.println("----")

                        missingDLFileVersions++

                        if (!safeMode) {
                            DLFileVersionLocalServiceUtil.deleteDLFileVersion(fileVersionId)
                            ExpandoRowLocalServiceUtil.deleteRows(fileVersionId)
                            WorkflowInstanceLinkLocalServiceUtil.deleteWorkflowInstanceLinks(
                                companyId, groupId, DLFileEntry.class.getName(), fileVersionId)

                            def latest = DLFileVersionLocalServiceUtil.fetchLatestFileVersion(fileEntryId, false)
                            if (latest == null) {
                                DLFileEntryLocalServiceUtil.deleteDLFileEntry(fileEntryId)
                                missingDLFileEntries++
                            }
                        }
                    }

                    rows++
                } finally {
                    sc.close()
                }
            }

            out.println("\nChecked ${rows} DLFileVersion entries")
            out.println("Missing physical files in DLStore: ${missingDLFileVersions}")
            if (!safeMode) {
                out.println("DLFileEntry records deleted: ${missingDLFileEntries}")
            }

        } catch (Exception e) {
            e.printStackTrace(out)
        } finally {
            DataAccess.cleanUp(con, ps, rs)
        }
    }

    String getFullFolderPath(long folderId) {
        if (folderId == DLFolderConstants.DEFAULT_PARENT_FOLDER_ID) {
            return "ROOT"
        }

        List<String> folderNames = []

        while (folderId != DLFolderConstants.DEFAULT_PARENT_FOLDER_ID) {
            def folder = DLFolderLocalServiceUtil.fetchDLFolder(folderId)
            if (folder == null) break
            folderNames.add(0, folder.getName())
            folderId = folder.getParentFolderId()
        }

        return "ROOT > " + folderNames.join(" > ")
    }
}

// Usage
CheckDLStore checkDLStore = new CheckDLStore(out)
checkDLStore.execute()
