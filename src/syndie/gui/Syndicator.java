package syndie.gui;

import java.util.HashMap;
import java.util.Map;
import net.i2p.data.Hash;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.TreeListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import syndie.Constants;
import syndie.data.SyndieURI;
import syndie.db.SyncArchive;
import syndie.db.SyncManager;

/**
 *
 */
public class Syndicator implements Translatable, Themeable, SyncManager.SyncListener, SyncArchive.SyncArchiveListener {
    private BrowserControl _browser;
    private Composite _parent;
    
    private Composite _root;
    private Composite _actions;
    private Tree _tree;
    private TreeColumn _colName;
    private TreeColumn _colTime;
    private TreeColumn _colStatus;
    private TreeColumn _colSummary;
    private Menu _treeMenu;
    private Composite _detailRoot;
    
    private Map _archiveNameToRootItem;
    private Map _archiveNameToIndexItem;
    /** name to TreeItem for the subtree item */
    private Map _archiveNameToIncomingItem;
    /** name to Map of SyndieURI to TreeItem for pending fetches */
    private Map _archiveNameToIncoming;
    /** name to TreeItem for the subtree item */
    private Map _archiveNameToOutgoingItem;
    /** name to Map of SyndieURI to TreeItem for pending fetches */
    private Map _archiveNameToOutgoing;

    /** 
     * map of TreeItem to descriptive detail about the item they represent.  The values
     * are of different types, depending on the item selected.  For archive roots,
     * they contain a SyncArchive.  For the elements directly under the archive root, they
     * contain a string ("fetchindex", "incoming", "outgoing"), and for elements under
     * the incoming and outgoing branches, they contain SyncArchive.InboundAction and 
     * SyncArchive.OutboundAction elements, respectively
     */
    private Map _items;
    
    private boolean _disposed;
    
    public Syndicator(BrowserControl browser, Composite parent) {
        _browser = browser;
        _parent = parent;
        
        _archiveNameToRootItem = new HashMap();
        _archiveNameToIndexItem = new HashMap();
        _archiveNameToIncomingItem = new HashMap();
        _archiveNameToIncoming = new HashMap();
        _archiveNameToOutgoingItem = new HashMap();
        _archiveNameToOutgoing = new HashMap();
        _items = new HashMap();
        
        _disposed = false;

        initComponents();
    }
    
    private void initComponents() {
        _root = new Composite(_parent, SWT.NONE);
        _root.setLayout(new GridLayout(1, true));
        
        _actions = new Composite(_root, SWT.NONE);
        _actions.setLayout(new FillLayout(SWT.HORIZONTAL));
        _actions.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false));
        
        // actual action buttons...
        new Button(_actions, SWT.PUSH).setText("Add archive");
        new Button(_actions, SWT.PUSH).setText("Pause all");
        new Button(_actions, SWT.PUSH).setText("Schedule one-off");
        new Button(_actions, SWT.PUSH).setText("Delete archive");
        
        _tree = new Tree(_root, SWT.FULL_SELECTION | SWT.BORDER | SWT.MULTI | SWT.V_SCROLL);
        _tree.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));
        _tree.setHeaderVisible(true);
        _tree.setLinesVisible(true);
        
        _colName = new TreeColumn(_tree, SWT.LEFT);
        _colTime = new TreeColumn(_tree, SWT.LEFT);
        _colStatus = new TreeColumn(_tree, SWT.CENTER);
        _colSummary = new TreeColumn(_tree, SWT.LEFT);
        
        SyndieTreeListener lsnr = new SyndieTreeListener(_tree) {
            public void selected() {
                TreeItem items[] = _tree.getSelection();
                if ( (items != null) && (items.length == 1) )
                    fireSelection(items[0]);
            }
        };
        _tree.addKeyListener(lsnr);
        _tree.addMouseListener(lsnr);
        _tree.addSelectionListener(lsnr);
        _tree.addTraverseListener(lsnr);
        
        _treeMenu = new Menu(_tree);
        _tree.setMenu(_treeMenu);
        _treeMenu.addMenuListener(new MenuListener() {
            public void menuHidden(MenuEvent menuEvent) {}
            public void menuShown(MenuEvent evt) { buildMenu(); }
        });
        
        _detailRoot = new Composite(_root, SWT.NONE);
        _detailRoot.setLayout(new FillLayout());
        _detailRoot.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, true));
        
        Text t = new Text(_detailRoot, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        t.setText("details here");
        
        _browser.getTranslationRegistry().register(this);
        _browser.getThemeRegistry().register(this);
        
        loadData();
    }
    
    public void show(SyndieURI uri) {
        if (uri.isArchive()) {
            SyncManager mgr = SyncManager.getInstance(_browser.getClient(), _browser.getUI());
            int cnt = mgr.getArchiveCount();
            for (int i = 0; i < cnt; i++) {
                SyncArchive archive = mgr.getArchive(i);
                if (archive.getURL().equals(uri.getURL())) {
                    TreeItem item = (TreeItem)_archiveNameToRootItem.get(archive.getName());
                    if (item != null) {
                        _tree.setSelection(item);
                        fireSelection(item);
                    }
                    return;
                }
            }
            // it isn't an existing archive, so show it in the details pane w/out a treeitem
            // todo: do that
        }
    }
    
    public void dispose() {
        _disposed = true;
        SyncManager mgr = SyncManager.getInstance(_browser.getClient(), _browser.getUI());
        mgr.removeListener(this);
        int idx = mgr.getArchiveCount();
        
        _browser.getTranslationRegistry().unregister(this);
        _browser.getThemeRegistry().unregister(this);
    }
    
    private void buildMenu() {
        // depending on what type of item is selected, we want to show different options
        MenuItem items[] = _treeMenu.getItems();
        for (int i = 0; i < items.length; i++) items[i].dispose();
        if (_tree.getSelectionCount() != 1) return;
        TreeItem item = _tree.getSelection()[0];
        Object val = _items.get(item);
        if (val == null) return;
        if (val instanceof SyncArchive) {
            buildMenuArchive((SyncArchive)val);
        } else if (val instanceof String) {
            if ("incoming".equals(val))
                buildMenuIncoming(item);
            else if ("outgoing".equals(val))
                buildMenuOutgoing(item);
            else if ("fetchindex".equals(val))
                buildMenuFetchIndex(item);
        } else if (val instanceof SyncArchive.IncomingAction) {
            buildMenuIncoming((SyncArchive.IncomingAction)val);
        } else if (val instanceof SyncArchive.OutgoingAction) {
            buildMenuOutgoing((SyncArchive.OutgoingAction)val);
        } else {
            // unknown type
            _browser.getUI().debugMessage("Unknown type is selected: " + val + " for " + item);
        }
    }
    private void fireSelection(TreeItem item) {
        // depending on what type of item is selected, show a different details panel
        Object val = _items.get(item);
        if (val == null) return;
        if (val instanceof SyncArchive) {
            viewDetailArchive((SyncArchive)val);
        } else if (val instanceof String) {
            if ("incoming".equals(val))
                viewDetailIncoming(item);
            else if ("outgoing".equals(val))
                viewDetailOutgoing(item);
            else if ("fetchindex".equals(val))
                viewDetailFetchIndex(item);
        } else if (val instanceof SyncArchive.IncomingAction) {
            viewDetailIncoming((SyncArchive.IncomingAction)val);
        } else if (val instanceof SyncArchive.OutgoingAction) {
            viewDetailOutgoing((SyncArchive.OutgoingAction)val);
        } else {
            // unknown type
            _browser.getUI().debugMessage("Unknown type is selected: " + val + " for " + item);
        }
    }
    
    private void buildMenuArchive(SyncArchive archive) {}
    private void buildMenuIncoming(TreeItem item) {}
    private void buildMenuOutgoing(TreeItem item) {}
    private void buildMenuFetchIndex(TreeItem item) {}
    private void buildMenuIncoming(SyncArchive.IncomingAction action) {
        if (action.isComplete()) {
            final SyndieURI uri = action.getURI();
            MenuItem item = new MenuItem(_treeMenu, SWT.PUSH);
            item.setText(_browser.getTranslationRegistry().getText(T_MENU_VIEW, "View"));
            item.addSelectionListener(new SelectionListener() {
                public void widgetDefaultSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
                public void widgetSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
            });
            if (action.getPBEPrompt() != null) {
                item = new MenuItem(_treeMenu, SWT.PUSH);
                item.setText(_browser.getTranslationRegistry().getText(T_MENU_PBE, "Resolve passphrase prompt"));
                item.addSelectionListener(new SelectionListener() {
                    public void widgetDefaultSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
                    public void widgetSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
                });
            }
        }
    }
    private void buildMenuOutgoing(SyncArchive.OutgoingAction action) {
        final SyndieURI uri = action.getURI();
        MenuItem item = new MenuItem(_treeMenu, SWT.PUSH);
        item.setText(_browser.getTranslationRegistry().getText(T_MENU_VIEW, "View"));
        item.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
            public void widgetSelected(SelectionEvent selectionEvent) { _browser.view(uri); }
        });
    }
    private static final String T_MENU_VIEW = "syndie.gui.syndicator.menu.view";
    private static final String T_MENU_PBE = "syndie.gui.syndicator.menu.pbe";
    
    private void viewDetailArchive(SyncArchive archive) {}
    private void viewDetailIncoming(TreeItem item) {}
    private void viewDetailOutgoing(TreeItem item) {}
    private void viewDetailFetchIndex(TreeItem item) {}
    private void viewDetailIncoming(SyncArchive.IncomingAction action) {}
    private void viewDetailOutgoing(SyncArchive.OutgoingAction action) {}
    
    private void loadData() {
        SyncManager mgr = SyncManager.getInstance(_browser.getClient(), _browser.getUI());
        mgr.loadArchives();
        mgr.addListener(this);
    
        int cnt = mgr.getArchiveCount();
        _browser.getUI().debugMessage("archives loaded: " + cnt);
        for (int i = 0; i < cnt; i++) {
            SyncArchive archive = mgr.getArchive(i);
            archive.addListener(this);
            loadData(archive);
        }
    }
    
    // create or update a subtree for the archive, including an item for the index fetch (if scheduled),
    // a subtree for scheduled message/meta fetches, and a subtree for scheduled pushes.  do we want
    // a subtree for uncleared imported messages/meta fetches?  or just keep them in the fetches subtree,
    // cleared on demand
    private void loadData(SyncArchive archive) {
        TreeItem rootItem = loadDataRoot(archive);
        loadDataIndex(archive, rootItem);
        loadDataFetches(archive, rootItem);
        loadDataPushes(archive);
    }
    
    private long getNextTime(SyncArchive archive) {
        if (archive.getNextPullTime() <= 0)
            return archive.getNextPushTime();
        else if (archive.getNextPushTime() <= 0)
            return archive.getNextPullTime();
        else
            return Math.min(archive.getNextPullTime(), archive.getNextPushTime());
    }
    
    private TreeItem loadDataRoot(SyncArchive archive) {
        long nextTime = getNextTime(archive);
        _browser.getUI().debugMessage("loadDataRoot(" + archive + "): nextTime in " + (nextTime-System.currentTimeMillis()) + "ms");
        TreeItem rootItem = (TreeItem)_archiveNameToRootItem.get(archive.getName());
        if (rootItem == null) {
            rootItem = new TreeItem(_tree, SWT.NONE);
            _archiveNameToRootItem.put(archive.getName(), rootItem);
            _items.put(rootItem, archive);
        }
        rootItem.setText(0, archive.getName());
        if (nextTime < 0) {
            rootItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_NEVER, "Never"));
            rootItem.setText(3, _browser.getTranslationRegistry().getText(T_SUMMARY_NEVER, "No syndication scheduled"));
        } else if (nextTime < System.currentTimeMillis()) {
            if (archive.getIndexFetchInProgress()) {
                rootItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_INDEXFETCHINPROGRESS, "Now"));
                rootItem.setText(3, _browser.getTranslationRegistry().getText(T_SUMMARY_INDEXFETCH, "Index fetch in progress"));
            } else {
                rootItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_INDEXFETCHCOMPLETE, "Now"));
                rootItem.setText(3, _browser.getTranslationRegistry().getText(T_SUMMARY_EXECUTEPOLICY, "Syndicating"));
            }
            rootItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_INPROGRESS);
        } else {
            rootItem.setText(1, Constants.getDateTime(nextTime));
            rootItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_SCHEDULED);
            rootItem.setText(3, _browser.getTranslationRegistry().getText(T_SUMMARY_SCHEDULED, "Syndication scheduled"));
        }
    
        resizeCols(rootItem);
        
        return rootItem;
    }
    
    private void loadDataIndex(SyncArchive archive, TreeItem rootItem) {
        long nextTime = getNextTime(archive);
        _browser.getUI().debugMessage("loadDataIndex(" + archive + "): nextTime in " + (nextTime-System.currentTimeMillis()) + "ms");
        
        TreeItem indexItem = (TreeItem)_archiveNameToIndexItem.get(archive.getName());
        if (nextTime <= 0) {
            if (indexItem != null)
                indexItem.dispose();
            _archiveNameToIndexItem.remove(archive.getName());
            return;
        }
        
        if (indexItem == null) {
            indexItem = new TreeItem(rootItem, SWT.NONE);
            _archiveNameToIndexItem.put(archive.getName(), indexItem);
            _items.put(indexItem, "fetchindex");
        }

        indexItem.setText(0, _browser.getTranslationRegistry().getText(T_INDEX_NAME, "Fetch index"));
        if (archive.getIndexFetchInProgress()) {
            indexItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_NOW, "Now"));
            indexItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_INPROGRESS);
            indexItem.setText(3, _browser.getTranslationRegistry().getText(T_SUMMARY_INDEXFETCH, "Index fetch in progress"));
        } else {
            indexItem.setText(1, Constants.getDateTime(nextTime));
        }
        if (!archive.getIndexFetchInProgress()) {
            if (archive.getLastIndexFetchErrorMsg() != null) {
                indexItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_ERROR);
                indexItem.setText(3, _browser.getTranslationRegistry().getText(T_INDEX_ERROR, "Fetch error: ") + archive.getLastIndexFetchErrorMsg());
            } else if (nextTime <= System.currentTimeMillis()) {
                indexItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_OK);
                indexItem.setText(3, _browser.getTranslationRegistry().getText(T_INDEX_COMPLETE, "Fetch complete"));
            } else {
                indexItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_SCHEDULED);
                indexItem.setText(3, _browser.getTranslationRegistry().getText(T_INDEX_SCHEDULED, "Scheduled"));
            }
        }
        resizeCols(indexItem);
    }
    
    private void loadDataFetches(SyncArchive archive, TreeItem rootItem) {
        int count = archive.getIncomingActionCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                SyncArchive.IncomingAction action = archive.getIncomingAction(i);
                loadDataFetch(action);
            }
        } else {
            TreeItem incomingItem = (TreeItem)_archiveNameToIncomingItem.remove(archive.getName());
            if (incomingItem != null)
                incomingItem.dispose();
            _archiveNameToIncoming.remove(archive.getName());
        }
    }

    /** create (or update) a fetch record (may create or update parents) */
    private void loadDataFetch(SyncArchive.IncomingAction action) {
        if (action != null) {
            SyndieURI uri = action.getURI();
            if (uri == null) return;
            Hash scope = uri.getScope();
            if (scope == null) return;

            Map incomingURIToItem = (Map)_archiveNameToIncoming.get(action.getArchive().getName());
            if (incomingURIToItem == null) {
                incomingURIToItem = new HashMap();
                _archiveNameToIncoming.put(action.getArchive().getName(), incomingURIToItem);
            }
            
            TreeItem incomingItem = (TreeItem)_archiveNameToIncomingItem.get(action.getArchive().getName());
            if (incomingItem == null) {
                TreeItem rootItem = loadDataRoot(action.getArchive());
                incomingItem = new TreeItem(rootItem, SWT.NONE);
                _archiveNameToIncomingItem.put(action.getArchive().getName(), incomingItem);
                _items.put(incomingItem, "incoming");
            }
            incomingItem.setText(0, _browser.getTranslationRegistry().getText(T_INCOMING_NAME, "scheduled fetches"));
            incomingItem.setText(3, action.getArchive().getIncomingActionCount()+"");
    
            resizeCols(incomingItem);
            
            TreeItem actionItem = (TreeItem)incomingURIToItem.get(uri);
            if (actionItem == null) {
                actionItem = new TreeItem(incomingItem, SWT.NONE);
                incomingURIToItem.put(uri, actionItem);
                _items.put(actionItem, action);
            }

            String forum = _browser.getClient().getChannelName(scope);
            if (forum != null)
                actionItem.setText(0, forum + " [" + scope.toBase64().substring(0,6) + "]" + (uri.getMessageId() != null ? " - " + uri.getMessageId() : ""));
            else
                actionItem.setText(0, "[" + scope.toBase64().substring(0,6) + "]" + (uri.getMessageId() != null ? " - " + uri.getMessageId() : ""));

            if (action.isScheduled()) {
                actionItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_ASAP, "ASAP"));
                actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_SCHEDULED);
            } else if (action.isExecuting()) {
                actionItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_NOW, "Now"));
                actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_INPROGRESS);
            } else { // complete
                actionItem.setText(1, Constants.getDateTime(action.getCompletionTime()));
                if (action.isReadKeyUnknown()) {
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_NOKEY);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_READKEYUNKNOWN, "Read key unknown"));
                } else if (action.isReplyKeyUnknown()) {
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_NOKEY);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_REPLYKEYUNKNOWN, "Reply key unknown"));
                } else if (action.getPBEPrompt() != null) {
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_PBE);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_PBE, "Passphrase required: ") + action.getPBEPrompt());
                } else if (action.isCorrupt()) {
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_ERROR);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_CORRUPT, "Message is corrupt"));
                } else if (action.getFetchErrorMsg() != null) {
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_ERROR);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_FAILED, "Fetch failed: ") + action.getFetchErrorMsg());
                } else { // ok                                
                    actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_OK);
                    actionItem.setText(3, _browser.getTranslationRegistry().getText(T_FETCH_OK, "Imported"));
                }
                
                resizeCols(actionItem);
            }
        }
    }
    
    private void loadDataPushes(SyncArchive archive) {
        int count = archive.getOutgoingActionCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                SyncArchive.OutgoingAction action = archive.getOutgoingAction(i);
                loadDataPush(action);
            }
        } else {
            TreeItem outgoingItem = (TreeItem)_archiveNameToOutgoingItem.remove(archive.getName());
            if (outgoingItem != null)
                outgoingItem.dispose();
            _archiveNameToOutgoing.remove(archive.getName());
        }
    }

    /** create (or update) a push record (may create or update parents) */
    private void loadDataPush(SyncArchive.OutgoingAction action) {
        if (action != null) {
            SyndieURI uri = action.getURI();
            if (uri == null) return;
            Hash scope = uri.getScope();
            if (scope == null) return;

            TreeItem outgoingItem = (TreeItem)_archiveNameToOutgoingItem.get(action.getArchive().getName());
            if (outgoingItem == null) {
                TreeItem rootItem = loadDataRoot(action.getArchive());
                outgoingItem = new TreeItem(rootItem, SWT.NONE);
                _archiveNameToOutgoingItem.put(action.getArchive().getName(), outgoingItem);
                _items.put(outgoingItem, "outgoing");
            }
            outgoingItem.setText(0, _browser.getTranslationRegistry().getText(T_OUTGOING_NAME, "scheduled pushes"));
            outgoingItem.setText(3, action.getArchive().getOutgoingActionCount()+"");

            Map outgoingURIToItem = (Map)_archiveNameToOutgoing.get(action.getArchive().getName());
            if (outgoingURIToItem == null) {
                outgoingURIToItem = new HashMap();
                _archiveNameToOutgoing.put(action.getArchive().getName(), outgoingURIToItem);
            }
            resizeCols(outgoingItem);
            
            TreeItem actionItem = (TreeItem)outgoingURIToItem.get(uri);
            if (actionItem == null) {
                actionItem = new TreeItem(outgoingItem, SWT.NONE);
                outgoingURIToItem.put(uri, actionItem);
                _items.put(actionItem, action);
            }

            String forum = _browser.getClient().getChannelName(scope);
            if (forum != null)
                actionItem.setText(0, forum + " [" + scope.toBase64().substring(0,6) + "]" + (uri.getMessageId() != null ? " - " + uri.getMessageId() : ""));
            else
                actionItem.setText(0, "[" + scope.toBase64().substring(0,6) + "]" + (uri.getMessageId() != null ? " - " + uri.getMessageId() : ""));

            if (action.isScheduled()) {
                actionItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_ASAP, "ASAP"));
                actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_SCHEDULED);
            } else if (action.isExecuting()) {
                actionItem.setText(1, _browser.getTranslationRegistry().getText(T_WHEN_NOW, "Now"));
                actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_INPROGRESS);
            } else { // complete
                actionItem.setText(1, Constants.getDateTime(action.getCompletionTime()));
                actionItem.setImage(2, ImageUtil.ICON_SYNDICATE_STATUS_OK);
                actionItem.setText(3, _browser.getTranslationRegistry().getText(T_PUSH_OK, "Pushed"));
            }
            resizeCols(actionItem);
        }
    }
    
    private void resizeCols(TreeItem item) {
        setMinWidth(_colName, item.getText(0), 0, 100);
        setMinWidth(_colTime, item.getText(1), 0, 100);
        setMinWidth(_colStatus, item.getText(2), 0, 50);
        setMinWidth(_colSummary, item.getText(3), 0, 100);
    }
    
    protected void setMinWidth(TreeColumn col, String txt, int extra, int min) {
        int width = ImageUtil.getWidth(txt, _tree) + _tree.getGridLineWidth()*2 + extra;
        if (width < min)
            width = min;
        int existing = col.getWidth();
        if (width > existing)
            col.setWidth(width);
    }
    
    private static final String T_WHEN_NEVER = "syndie.gui.syndicator.when.never";
    private static final String T_WHEN_INDEXFETCHINPROGRESS = "syndie.gui.syndicator.when.indexfetchinprogress";
    private static final String T_WHEN_INDEXFETCHCOMPLETE = "syndie.gui.syndicator.when.indexfetchcomplete";
    private static final String T_SUMMARY_NEVER = "syndie.gui.syndicator.summary.never";
    private static final String T_SUMMARY_INDEXFETCH = "syndie.gui.syndicator.summary.indexfetch";
    private static final String T_SUMMARY_EXECUTEPOLICY = "syndie.gui.syndicator.summary.executepolicy";
    private static final String T_SUMMARY_SCHEDULED = "syndie.gui.syndicator.summary.scheduled";
    
    private static final String T_FETCH_READKEYUNKNOWN = "syndie.gui.syndicator.fetch.readkeyunknown";
    private static final String T_FETCH_REPLYKEYUNKNOWN = "syndie.gui.syndicator.fetch.replykeyunknown";
    private static final String T_FETCH_PBE = "syndie.gui.syndicator.fetch.pbe";
    private static final String T_FETCH_OK = "syndie.gui.syndicator.fetch.ok";
    private static final String T_FETCH_FAILED = "syndie.gui.syndicator.fetch.failed";
    private static final String T_FETCH_CORRUPT = "syndie.gui.syndicator.fetch.corrupt";
    
    private static final String T_PUSH_OK = "syndie.gui.syndicator.push.ok";

    private static final String T_WHEN_NOW = "syndie.gui.syndicator.now";
    private static final String T_WHEN_ASAP = "syndie.gui.syndicator.asap";
    
    private static final String T_INDEX_NAME = "syndie.gui.syndicator.index.name";
    private static final String T_INDEX_ERROR = "syndie.gui.syndicator.index.error";
    private static final String T_INDEX_COMPLETE = "syndie.gui.syndicator.index.complete";
    private static final String T_INDEX_SCHEDULED = "syndie.gui.syndicator.index.scheduled";
    
    private static final String T_INCOMING_NAME = "syndie.gui.syndicator.incoming.name";
    private static final String T_OUTGOING_NAME = "syndie.gui.syndicator.outgoing.name";
    
    private static final String T_COLNAME = "syndie.gui.syndicator.colname";
    private static final String T_COLTIME = "syndie.gui.syndicator.coltime";
    private static final String T_COLSTATUS = "syndie.gui.syndicator.colstatus";
    private static final String T_COLSUMMARY = "syndie.gui.syndicator.colsummary";
    
    public void translate(TranslationRegistry registry) {
        _colName.setText(registry.getText(T_COLNAME, ""));
        _colTime.setText(registry.getText(T_COLTIME, "Time"));
        _colStatus.setText(registry.getText(T_COLSTATUS, "Status"));
        _colSummary.setText(registry.getText(T_COLSUMMARY, "Summary"));
    }
    
    public void applyTheme(Theme theme) {
        _tree.setFont(theme.TREE_FONT);
        
        _colName.pack();
        _colTime.pack();
        _colStatus.pack();
        _colSummary.pack();
    }

    //
    // now for the SyncArchive/SyncManager callbacks (will be called from arbitrary threads
    //
    
    public void archiveAdded(final SyncArchive archive) {
        if (_disposed) return;
        _browser.getUI().debugMessage("archive added: " + archive);
        archive.addListener(this);
        Display.getDefault().syncExec(new Runnable() { public void run() { loadData(archive); } });
    }

    public void archiveRemoved(final SyncArchive archive) {
        if (_disposed) return;
        _browser.getUI().debugMessage("archive removed: " + archive);
        Display.getDefault().syncExec(new Runnable() { 
            public void run() { 
                // putting these map removals in the swt thread means we can skip synchronization
                TreeItem rootItem = (TreeItem)_archiveNameToRootItem.remove(archive.getName());
                _archiveNameToIncoming.remove(archive.getName());
                _archiveNameToIncomingItem.remove(archive.getName());
                _archiveNameToIndexItem.remove(archive.getName());
                _archiveNameToOutgoing.remove(archive.getName());
                _archiveNameToOutgoingItem.remove(archive.getName());
                if (rootItem != null)
                    rootItem.dispose(); 
            } 
        });
    }

    public void archiveLoaded(final SyncArchive archive) {
        if (_disposed) return;
        _browser.getUI().debugMessage("archive loaded: " + archive);
        archive.addListener(this);
        Display.getDefault().syncExec(new Runnable() { public void run() { loadData(archive); } });
    }

    public void incomingUpdated(final SyncArchive.IncomingAction action) {
        if (_disposed) return;
        _browser.getUI().debugMessage("incoming action updated: " + action);
        Display.getDefault().syncExec(new Runnable() { public void run() { loadDataFetch(action); } });
    }

    public void outgoingUpdated(final SyncArchive.OutgoingAction action) {
        if (_disposed) return;
        _browser.getUI().debugMessage("outgoing action updated: " + action);
        Display.getDefault().syncExec(new Runnable() { public void run() { loadDataPush(action); } });
    }

    public void archiveUpdated(final SyncArchive archive) {
        if (_disposed) return;
        _browser.getUI().debugMessage("archiveUpdated(" + archive + ")");
        Display.getDefault().syncExec(new Runnable() { public void run() { loadData(archive); } });
    }

    public void onlineStatusUpdated(boolean nowOnline) {}
}