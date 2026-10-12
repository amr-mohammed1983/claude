package dev.filenetmcp.jace;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.collection.DocumentSet;
import com.filenet.api.collection.FolderSet;
import com.filenet.api.collection.ObjectStoreSet;
import com.filenet.api.collection.RepositoryRowSet;
import com.filenet.api.constants.AutoClassify;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.CheckinType;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.constants.ReservationType;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Connection;
import com.filenet.api.core.Document;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.IndependentObject;
import com.filenet.api.core.IndependentlyPersistableObject;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.ReferentialContainmentRelationship;
import com.filenet.api.core.Versionable;
import com.filenet.api.meta.ClassDescription;
import com.filenet.api.meta.PropertyDescription;
import com.filenet.api.property.Properties;
import com.filenet.api.property.Property;
import com.filenet.api.query.RepositoryRow;
import com.filenet.api.query.SearchSQL;
import com.filenet.api.query.SearchScope;
import com.filenet.api.util.UserContext;
import dev.filenetmcp.FileNetProperties;
import dev.filenetmcp.gateway.FileNetGateway;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.security.auth.Subject;

/**
 * {@link FileNetGateway} backed by the FileNet P8 5.7 Content Engine Java API (Jace.jar).
 *
 * <p>Every public method goes through {@link #call}, which pushes the authenticated JAAS
 * subject on the current thread (UserContext is thread-local) and always pops it again.
 * Object access is class-agnostic ({@link #fetch}) so documents, folders and custom objects
 * share one code path.
 */
public final class JaceGateway implements FileNetGateway {
    private final FileNetProperties config;
    private final Map<String, ObjectStore> stores = new ConcurrentHashMap<>();
    private volatile Connection connection;
    private volatile Subject subject;
    private volatile Domain domain;

    public JaceGateway(FileNetProperties config) {
        if (config.uri() == null || config.uri().isBlank()) {
            throw new IllegalArgumentException("FILENET_URI is required, e.g. https://host:9443/wsi/FNCEWS40MTOM/");
        }
        this.config = config;
    }

    // ---- connection / session ------------------------------------------------------------

    private synchronized void connect() {
        if (connection != null) return;
        Connection con = Factory.Connection.getConnection(config.uri());
        subject = UserContext.createSubject(con, config.username(), config.password(), config.jaasStanza());
        connection = con;
    }

    private <T> T call(Supplier<T> action) {
        connect();
        UserContext uc = UserContext.get();
        uc.pushSubject(subject);
        try {
            return action.get();
        } finally {
            uc.popSubject();
        }
    }

    private void run(Runnable action) {
        call(() -> { action.run(); return null; });
    }

    private Domain domain() {
        if (domain == null) domain = Factory.Domain.fetchInstance(connection, null, null);
        return domain;
    }

    private ObjectStore store(String name) {
        return stores.computeIfAbsent(name, n -> Factory.ObjectStore.fetchInstance(domain(), n, null));
    }

    private static com.filenet.api.property.PropertyFilter filter(String names) {
        com.filenet.api.property.PropertyFilter f = new com.filenet.api.property.PropertyFilter();
        f.addIncludeProperty(new com.filenet.api.property.FilterElement(null, null, null, names, null));
        return f;
    }

    // ---- shared helpers -------------------------------------------------------------------

    /** Fetches any object by class + GUID/path; {@code names} empty = all properties. */
    private IndependentObject fetch(String os, String className, String ref, List<String> names) {
        IndependentObject o = store(os).getObject(className, ref);
        if (names.isEmpty()) o.refresh(); else o.refresh(names.toArray(new String[0]));
        return o;
    }

    private Document fetchDocument(String os, String ref, String names) {
        return Factory.Document.fetchInstance(store(os), ref, filter(names));
    }

    private static void save(IndependentObject o) {
        ((IndependentlyPersistableObject) o).save(RefreshMode.REFRESH);
    }

    /** JSON-friendly view of a property bag. */
    private static Map<String, Object> toMap(Properties props) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Iterator<?> it = props.iterator(); it.hasNext(); ) {
            Property p = (Property) it.next();
            Object v = p.getObjectValue();
            if (v != null) out.put(p.getPropertyName(), json(v));
        }
        return out;
    }

    private static Object json(Object v) {
        if (v instanceof String || v instanceof Number || v instanceof Boolean) return v;
        if (v instanceof Date d) return d.toInstant().toString();
        if (v instanceof List<?> l) return l.stream().map(JaceGateway::json).toList();
        if (v instanceof IndependentObject io) return String.valueOf(io.getObjectReference().getObjectIdentity());
        if (v instanceof Iterable<?>) return "[collection - fetch explicitly]";
        return v.toString(); // Id, Guid, enums...
    }

    /** Writes MCP-supplied values into CE properties. {"$date": ISO-8601} becomes a Date. */
    private static void apply(Properties target, Map<String, Object> values) {
        values.forEach((name, v) -> target.putObjectValue(name, convert(v)));
    }

    private static Object convert(Object v) {
        if (v instanceof Map<?, ?> m && m.get("$date") != null) return Date.from(java.time.Instant.parse(m.get("$date").toString()));
        if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return l.intValue();
        if (v instanceof List<?> l) return l.stream().map(JaceGateway::convert).toList();
        return v;
    }

    private static void attach(Document doc, Content c) {
        ContentTransfer ct = Factory.ContentTransfer.createInstance();
        ct.setCaptureSource(new ByteArrayInputStream(c.data()));
        ct.set_ContentType(c.mimeType());
        ct.set_RetrievalName(c.fileName());
        ContentElementList list = Factory.ContentElement.createList();
        list.add(ct);
        doc.set_ContentElements(list);
    }

    private static Map<String, Object> summary(IndependentObject o, String... names) {
        return toMap(o.getProperties()).entrySet().stream()
                .filter(e -> names.length == 0 || List.of(names).contains(e.getKey()))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    // ---- FileNetGateway --------------------------------------------------------------------

    @Override
    public List<Map<String, Object>> listObjectStores() {
        return call(() -> {
            List<Map<String, Object>> out = new ArrayList<>();
            ObjectStoreSet set = domain().get_ObjectStores();
            for (Iterator<?> it = set.iterator(); it.hasNext(); ) {
                ObjectStore s = (ObjectStore) it.next();
                out.add(Map.of("symbolicName", s.get_SymbolicName(), "displayName", String.valueOf(s.get_DisplayName())));
            }
            return out;
        });
    }

    @Override
    public Map<String, Object> getObject(String os, String className, String ref, List<String> properties) {
        return call(() -> toMap(fetch(os, className, ref, properties).getProperties()));
    }

    @Override
    public List<Map<String, Object>> search(String os, String sql, int maxRows) {
        return call(() -> {
            RepositoryRowSet rows = new SearchScope(store(os)).fetchRows(new SearchSQL(sql), Math.min(maxRows, 100), null, Boolean.TRUE);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Iterator<?> it = rows.iterator(); it.hasNext() && out.size() < maxRows; ) {
                out.add(toMap(((RepositoryRow) it.next()).getProperties()));
            }
            return out;
        });
    }

    @Override
    public Map<String, Object> createDocument(String os, String className, Map<String, Object> properties,
                                              String folderPath, String containmentName, Content content, boolean checkin) {
        return call(() -> {
            Document doc = Factory.Document.createInstance(store(os), className);
            apply(doc.getProperties(), properties);
            if (content != null) {
                attach(doc, content);
                doc.set_MimeType(content.mimeType());
            }
            if (checkin) doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
            doc.save(RefreshMode.REFRESH);
            if (folderPath != null) {
                Folder folder = Factory.Folder.fetchInstance(store(os), folderPath, null);
                ReferentialContainmentRelationship rcr = folder.file(doc, AutoUniqueName.AUTO_UNIQUE,
                        containmentName, DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
                rcr.save(RefreshMode.NO_REFRESH);
            }
            return summary(doc, "Id", "Name", "DocumentTitle", "MajorVersionNumber", "MinorVersionNumber");
        });
    }

    @Override
    public Map<String, Object> createFolder(String os, String className, String parentPath, String name,
                                            Map<String, Object> properties) {
        return call(() -> {
            Folder folder = Factory.Folder.createInstance(store(os), className);
            apply(folder.getProperties(), properties);
            folder.set_Parent(Factory.Folder.fetchInstance(store(os), parentPath, null));
            folder.set_FolderName(name);
            folder.save(RefreshMode.REFRESH);
            return summary(folder, "Id", "FolderName", "PathName");
        });
    }

    @Override
    public Map<String, Object> updateObject(String os, String className, String ref, Map<String, Object> properties) {
        return call(() -> {
            IndependentObject o = fetch(os, className, ref, new ArrayList<>(properties.keySet()));
            apply(o.getProperties(), properties);
            save(o);
            return toMap(o.getProperties());
        });
    }

    @Override
    public void deleteObject(String os, String className, String ref) {
        run(() -> {
            IndependentObject o = fetch(os, className, ref, List.of("Id"));
            ((IndependentlyPersistableObject) o).delete();
            save(o);
        });
    }

    @Override
    public Map<String, Object> listFolder(String os, String folderRef) {
        return call(() -> {
            Folder folder = Factory.Folder.fetchInstance(store(os), folderRef, filter("Id FolderName PathName SubFolders ContainedDocuments"));
            List<Map<String, Object>> subs = new ArrayList<>(), docs = new ArrayList<>();
            FolderSet fs = folder.get_SubFolders();
            for (Iterator<?> it = fs.iterator(); it.hasNext(); ) subs.add(summary((Folder) it.next(), "Id", "FolderName", "PathName"));
            DocumentSet ds = folder.get_ContainedDocuments();
            for (Iterator<?> it = ds.iterator(); it.hasNext(); ) docs.add(summary((Document) it.next(), "Id", "Name", "MimeType", "ContentSize"));
            Map<String, Object> out = summary(folder, "Id", "FolderName", "PathName");
            out.put("subFolders", subs);
            out.put("documents", docs);
            return out;
        });
    }

    @Override
    public Content getContent(String os, String documentRef, int elementIndex) {
        return call(() -> {
            Document doc = fetchDocument(os, documentRef, "ContentElements Name MimeType");
            ContentElementList list = doc.get_ContentElements();
            if (elementIndex < 0 || elementIndex >= list.size()) {
                throw new IllegalArgumentException("Document has " + list.size() + " content element(s); index " + elementIndex + " is out of range");
            }
            ContentTransfer ct = (ContentTransfer) list.get(elementIndex);
            try (InputStream in = ct.accessContentStream()) {
                String name = ct.get_RetrievalName() != null ? ct.get_RetrievalName() : doc.get_Name();
                return new Content(name, ct.get_ContentType(), in.readAllBytes());
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    @Override
    public Map<String, Object> checkout(String os, String documentRef) {
        return call(() -> {
            Document doc = fetchDocument(os, documentRef, "Id Name IsReserved");
            doc.checkout(ReservationType.EXCLUSIVE, null, doc.getClassName(), null);
            doc.save(RefreshMode.REFRESH);
            Document reservation = (Document) doc.get_Reservation();
            Map<String, Object> out = summary(reservation, "Id", "Name", "IsReserved");
            out.put("reservationId", String.valueOf(reservation.get_Id()));
            return out;
        });
    }

    @Override
    public Map<String, Object> cancelCheckout(String os, String documentRef) {
        return call(() -> {
            Document doc = fetchDocument(os, documentRef, "Id Name IsReserved Reservation");
            Versionable released = doc.cancelCheckout();
            released.save(RefreshMode.REFRESH);
            return summary(released, "Id", "Name", "IsReserved");
        });
    }

    @Override
    public Map<String, Object> checkin(String os, String documentRef, Map<String, Object> properties,
                                       Content content, boolean majorVersion) {
        return call(() -> {
            Document doc = fetchDocument(os, documentRef, "Id Name IsReserved Reservation");
            Document reservation = doc.get_Reservation() != null ? (Document) doc.get_Reservation() : doc;
            apply(reservation.getProperties(), properties);
            if (content != null) attach(reservation, content);
            reservation.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY,
                    majorVersion ? CheckinType.MAJOR_VERSION : CheckinType.MINOR_VERSION);
            reservation.save(RefreshMode.REFRESH);
            return summary(reservation, "Id", "Name", "MajorVersionNumber", "MinorVersionNumber");
        });
    }

    @Override
    public List<Map<String, Object>> describeClass(String os, String className) {
        return call(() -> {
            ClassDescription cd = Factory.ClassDescription.fetchInstance(store(os), className, null);
            List<Map<String, Object>> out = new ArrayList<>();
            for (Iterator<?> it = cd.get_PropertyDescriptions().iterator(); it.hasNext(); ) {
                PropertyDescription pd = (PropertyDescription) it.next();
                Map<String, Object> m = new HashMap<>();
                m.put("name", pd.get_SymbolicName());
                m.put("type", String.valueOf(pd.get_DataType()));
                m.put("cardinality", String.valueOf(pd.get_Cardinality()));
                m.put("required", pd.get_IsValueRequired());
                out.add(m);
            }
            return out;
        });
    }
}
