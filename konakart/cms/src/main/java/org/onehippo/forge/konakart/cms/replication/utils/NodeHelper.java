package org.onehippo.forge.konakart.cms.replication.utils;

import com.konakart.app.Product;
import com.konakart.appif.ManufacturerIf;
import com.konakartadmin.app.AdminCustomer;
import org.hippoecm.repository.api.*;
import org.hippoecm.repository.standardworkflow.DefaultWorkflow;
import org.hippoecm.repository.standardworkflow.FolderWorkflow;
import org.onehippo.forge.konakart.common.KKCndConstants;
import org.onehippo.forge.konakart.common.util.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.version.VersionManager;
import java.rmi.RemoteException;
import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NodeHelper {

  public static final String PUBLISHED_STATE = "published";
  public static final String UNPUBLISHED_STATE = "unpublished";

  public static final Logger log = LoggerFactory.getLogger(NodeHelper.class);


  /**
   * Hippo Repository specific predefined folder node type name
   */
  protected String folderNodeTypeName = "hippostd:folder";

  /**
   * The workflow category name to get a folder workflow. We use threepane as this is the same as the CMS uses
   */
  protected String folderNodeWorkflowCategory = "threepane";

  /**
   * The workflow category name to add a new document.
   */
  protected String documentAdditionWorkflowCategory = "new-document";

  /**
   * The workflow category name to add a new folder.
   */
  protected String folderAdditionWorkflowCategory = "new-folder";

  /**
   * The workflow category name to localize the new document
   */
  protected String defaultWorkflowCategory = "core";

  protected Session session;

  private VersionManager versionManager;

  public NodeHelper(Session session) throws RepositoryException {
    this.session = session;
    versionManager = session.getWorkspace().getVersionManager();
  }

  public void setFolderNodeTypeName(String folderNodeTypeName) {
    this.folderNodeTypeName = folderNodeTypeName;
  }

  public void setDocumentAdditionWorkflowCategory(String documentAdditionWorkflowCategory) {
    this.documentAdditionWorkflowCategory = documentAdditionWorkflowCategory;
  }

  public void setFolderAdditionWorkflowCategory(String folderAdditionWorkflowCategory) {
    this.folderAdditionWorkflowCategory = folderAdditionWorkflowCategory;
  }

  public Node createMissingFolders(String absPath) throws Exception {
    String[] folderNames = absPath.split("/");

    Node rootNode = session.getRootNode();
    Node curNode = rootNode;
    String folderNodePath;

    for (String folderName : folderNames) {
      String folderNodeName = Codecs.encodeNode(folderName);

      if (!"".equals(folderNodeName)) {
        if (curNode.equals(rootNode)) {
          folderNodePath = "/" + folderNodeName;
        } else {
          folderNodePath = curNode.getPath() + "/" + folderNodeName;
        }

        if (!session.itemExists(folderNodePath)) {
          curNode = session.getNode(createNodeByWorkflow(curNode, folderNodeTypeName, folderName));
        } else {
          curNode = curNode.getNode(folderNodeName);
        }

        if (curNode.isNodeType(HippoNodeType.NT_FACETSELECT) || curNode.isNodeType(HippoNodeType.NT_MIRROR)) {
          String docbaseUuid = curNode.getProperty("hippo:docbase").getString();
          // check whether docbaseUuid is a valid uuid, otherwise a runtime IllegalArgumentException is thrown
          try {
            UUID.fromString(docbaseUuid);
          } catch (IllegalArgumentException e) {
            throw new Exception("hippo:docbase in mirror does not contain a valid uuid", e);
          }
          // this is always the canonical
          curNode = session.getNodeByIdentifier(docbaseUuid);
        } else {
          curNode = getCanonicalNode(curNode);
        }
      }
    }

    return curNode;
  }

  @SuppressWarnings("rawtypes")
  protected String createNodeByWorkflow(Node folderNode, String nodeTypeName, String name) throws Exception {
    try {
      folderNode = getCanonicalNode(folderNode);
      Workflow wf = getWorkflow(folderNodeWorkflowCategory, folderNode);

      if (wf instanceof FolderWorkflow) {
        FolderWorkflow fwf = (FolderWorkflow) wf;

        String category = documentAdditionWorkflowCategory;


        if (nodeTypeName.equals(folderNodeTypeName)) {
          category = folderAdditionWorkflowCategory;

          // now check if there is some more specific workflow for hippostd:folder
          if (fwf.hints() != null && fwf.hints().get("prototypes") != null) {
            Object protypesMap = fwf.hints().get("prototypes");
            if (protypesMap instanceof Map) {
              for (Object o : ((Map) protypesMap).entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                if (entry.getKey() instanceof String && entry.getValue() instanceof Set) {
                  if (((Set) entry.getValue()).contains(folderNodeTypeName)) {
                    // we found possibly a more specific workflow for folderNodeTypeName. Use the key as category
                    category = (String) entry.getKey();
                    break;
                  }
                }
              }
            }
          }
        }

        String nodeName = Codecs.encodeNode(name);
        String added = fwf.add(category, nodeTypeName, nodeName);
        if (added == null) {
          throw new Exception("Failed to add document/folder for type '" + nodeTypeName
              + "'. Make sure there is a prototype.");
        }
        Item addedDocumentVariant = folderNode.getSession().getItem(added);
        if (addedDocumentVariant instanceof Node && !nodeName.equals(name)) {
          DefaultWorkflow defaultWorkflow = (DefaultWorkflow) getWorkflow(defaultWorkflowCategory, (Node) addedDocumentVariant);
          defaultWorkflow.localizeName(name);
        }
        return added;
      } else {
        throw new Exception("Can't add folder " + name + " [" + nodeTypeName + "] in the folder " + folderNode.getPath() + ", because there is no FolderWorkflow possible on the folder node: " + wf);
      }
    } catch (RepositoryException e) {
      throw new Exception(e);
    } catch (RemoteException e) {
      throw new Exception(e);
    } catch (WorkflowException e) {
      throw new Exception(e);
    }
  }

  public Node createOrRetrieveDocument(Node parentNode, Product product, String docType, String ownerId, String locale) throws RepositoryException {

    // Encode the name to be able to add name with special characters
    String encodingName = Codecs.encodeNode(product.getName());

    if (parentNode.hasNode(encodingName)) {
      Node handleNode = parentNode.getNode(encodingName);

      if (handleNode.hasNode(encodingName)) {
        Node currentNode = handleNode.getNode(encodingName);

        // we need to check if two different products have been created using the same name
        int productId = (int) currentNode.getProperty(KKCndConstants.PRODUCT_ID).getLong();

        if (product.getId() == productId) {
          return currentNode;
        }

        // Not the same id - so a new product will be created
        // continue the process used to create a new product's node.
      } else {
        return null;
      }
    }

    // Create the handle
    Node handle = parentNode.addNode(encodingName, "hippo:handle");
    handle.addMixin("hippo:hardhandle");
    handle.addMixin("hippo:translated");

    // Add translation node. This node is used to manager special name
    Node translation = handle.addNode("hippo:translation", "hippo:translation");
    translation.setProperty("hippo:language", "");
    translation.setProperty("hippo:message", product.getName());

    // Create the user
    Node childNode = handle.addNode(encodingName, docType);

    // Add mixin
    childNode.addMixin("hippo:harddocument");
    childNode.addMixin("hippotranslation:translated");

    // Add extra definitions
    childNode.setProperty("hippo:availability", new String[]{"live", "preview"});
    childNode.setProperty("hippotranslation:id", UUID.randomUUID().toString());
    childNode.setProperty("hippotranslation:locale", locale);
    childNode.setProperty("hippostdpubwf:lastModifiedBy", "admin");
    childNode.setProperty("hippostd:holder", "admin");
    childNode.setProperty("hippostdpubwf:lastModificationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:creationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:publicationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:createdBy", ownerId);

    return childNode;

  }

  public Node createOrRetrieveDocument(Node parentNode, ManufacturerIf manufacturer, String docType, String ownerId, String locale) throws RepositoryException {

    // Encode the name to be able to add name with special characters
    String encodingName = Codecs.encodeNode(manufacturer.getName());

    if (parentNode.hasNode(encodingName)) {
      Node handleNode = parentNode.getNode(encodingName);

      if (handleNode.hasNode(encodingName)) {
        return handleNode.getNode(encodingName);
      }

      return null;
    }

    // Create the handle
    Node handle = parentNode.addNode(encodingName, "hippo:handle");
    handle.addMixin("hippo:hardhandle");
    handle.addMixin("hippo:translated");

    // Add translation node. This node is used to manager special name
    Node translation = handle.addNode("hippo:translation", "hippo:translation");
    translation.setProperty("hippo:language", "");
    translation.setProperty("hippo:message", manufacturer.getName());

    // Create the user
    Node childNode = handle.addNode(encodingName, docType);

    // Add mixin
    childNode.addMixin("hippo:harddocument");
    childNode.addMixin("hippotranslation:translated");

    // Add extra definitions
    childNode.setProperty("hippo:availability", new String[]{"live", "preview"});
    childNode.setProperty("hippotranslation:id", UUID.randomUUID().toString());
    childNode.setProperty("hippotranslation:locale", locale);
    childNode.setProperty("hippostdpubwf:lastModifiedBy", "admin");
    childNode.setProperty("hippostd:holder", "admin");
    childNode.setProperty("hippostdpubwf:lastModificationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:creationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:publicationDate", new GregorianCalendar());
    childNode.setProperty("hippostdpubwf:createdBy", ownerId);

    return childNode;

  }

  public Node createOrRetrieveCustomer(AdminCustomer adminCustomer, int dirLevels) throws RepositoryException {

    String usersPath = "hippo:configuration/hippo:users";

    Node usersNode = session.getRootNode().getNode(usersPath);

    String userId = adminCustomer.getEmailAddr();

    int length = userId.length();
    int pos = 0;
    for (int i = 0; i < dirLevels; i++) {
      if (i < length) {
        pos = i;
      }
      String c = NodeNameCodec.encode(Character.toLowerCase(userId.charAt(pos)));
      if (!usersNode.hasNode(c)) {
        usersNode = usersNode.addNode(c, HippoNodeType.NT_USERFOLDER);
      } else {
        usersNode = usersNode.getNode(c);
      }
    }

    Node user;

    if (usersNode.hasNode(userId)) {
      user = usersNode.getNode(userId);
    } else {
      user = usersNode.addNode(userId, "hipposys:user");
    }

    // Add extra definitions
    user.setProperty("hipposys:securityprovider", KKCndConstants.KONAKART_SECURITY_PROVIDER);
    user.setProperty("hipposys:active", adminCustomer.isEnabled());
    user.setProperty("hipposys:firstname", adminCustomer.getFirstName());
    user.setProperty("hipposys:lastname", adminCustomer.getLastName());
    user.setProperty("hipposys:email", adminCustomer.getEmailAddr());
    user.setProperty("hipposys:password", SecurityUtils.createSyncKonakartPassword());

    return usersNode;
  }


  /**
   * Update the hippostd state
   *
   * @param state the state of the document
   */
  public void updateState(Node node, String state) throws RepositoryException {

    boolean hasCheckout = false;

    // Check if the node is check-in
    if (!node.isCheckedOut()) {
      checkout(node.getPath());
      hasCheckout = true;
    }

    node.setProperty("hippostd:state", state);

    if (state.equals(UNPUBLISHED_STATE)) {
      node.setProperty("hippo:availability", new String[]{"preview"});
    } else {
      node.setProperty("hippo:availability", new String[]{"live", "preview"});
    }

    if (hasCheckout) {
      checkin(node.getPath());
    }

  }

  public String getNodeState(Node node) throws RepositoryException {

    if (node.hasProperty("hippostd:state")) {
      return node.getProperty("hippostd:state").getString();
    }

    return null;
  }

  public Workflow getWorkflow(String category, Node node) throws RepositoryException {
    Workspace workspace = session.getWorkspace();

    ClassLoader workspaceClassloader = workspace.getClass().getClassLoader();
    ClassLoader currentClassloader = Thread.currentThread().getContextClassLoader();

    try {
      if (!workspaceClassloader.equals(currentClassloader)) {
        Thread.currentThread().setContextClassLoader(workspaceClassloader);
      }

      WorkflowManager wfm = ((HippoWorkspace) workspace).getWorkflowManager();
      return wfm.getWorkflow(category, node);
    } catch (RepositoryException e) {
      throw e;
    } catch (Exception e) {
      // other exception which are not handled properly in the repository (we cannot do better here then just log them)
      if (log.isDebugEnabled()) {
        log.warn("Exception in workflow", e);
      } else {
        log.warn("Exception in workflow: {}", e.toString());
      }
    } finally {
      if (workspaceClassloader.equals(currentClassloader)) {
        Thread.currentThread().setContextClassLoader(currentClassloader);
      }
    }

    return null;
  }


  private Node getCanonicalNode(Node folderNode) {
    if (folderNode instanceof HippoNode) {
      HippoNode hnode = (HippoNode) folderNode;
      try {
        Node canonical = hnode.getCanonicalNode();
        if (canonical == null) {
          log.debug("Cannot get canonical node for '{}'. This means there is no phyiscal equivalence of the " +
              "virtual node. Return null", folderNode.getPath());
        }
        return canonical;
      } catch (RepositoryException e) {
        throw new RuntimeException(e);
      }
    }
    return folderNode;
  }

  public void checkout(String path) throws RepositoryException {
    versionManager.checkout(path);
  }

  public void checkin(String path) throws RepositoryException {
    versionManager.checkin(path);
  }
}
