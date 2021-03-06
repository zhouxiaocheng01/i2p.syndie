package syndie.gui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Shell;
import syndie.data.SyndieURI;

/**
 *
 */
class MessageChooserPopup implements MessageTree.MessageTreeListener, Themeable, Translatable {
    private Shell _parentShell;
    private Shell _shell;
    private ThemeRegistry _themeRegistry;
    private TranslationRegistry _translationRegistry;
    private MessageTree _tree;
    private MessageTree.MessageTreeListener _listener;
    private Button _ok;
    private Button _cancel;
    
    /** Creates a new instance of MessageChooserPopup */
    public MessageChooserPopup(Shell parentShell, MessageTree.MessageTreeListener lsnr, ThemeRegistry themes, TranslationRegistry trans) {
        _parentShell = parentShell;
        _listener = lsnr;
        _themeRegistry = themes;
        _translationRegistry = trans;
        initComponents();
    }
    
    private void initComponents() {
        _shell = new Shell(_parentShell, SWT.SHELL_TRIM);
        _shell.setText("Message chooser");
        _shell.setLayout(new GridLayout(2, true));
        _tree = ComponentBuilder.instance().createMessageTree(_shell, this);
        GridData gd = new GridData(GridData.FILL, GridData.FILL, true, true, 2, 1);
        gd.widthHint = 300;
        gd.heightHint = 200;
        _tree.getControl().setLayoutData(gd);

        _cancel = new Button(_shell, SWT.PUSH);
        _cancel.setText(_translationRegistry.getText("Cancel"));
        _cancel.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false));
        _cancel.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent selectionEvent) { hide(); }
            public void widgetSelected(SelectionEvent selectionEvent) { hide(); }
        });
        
        _ok = new Button(_shell, SWT.PUSH);
        _ok.setText("ok");
        _ok.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false));
        _ok.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent selectionEvent) { 
                SyndieURI uri = _tree.getSelected();
                if (uri == null)
                    hide();
                else
                    messageSelected(_tree, uri, true, true);
            }
            public void widgetSelected(SelectionEvent selectionEvent) {
                SyndieURI uri = _tree.getSelected();
                if (uri == null)
                    hide();
                else
                    messageSelected(_tree, uri, true, true);
            }
        });
        
        // intercept the shell closing, since that'd cause the shell to be disposed rather than just hidden
        _shell.addShellListener(new ShellListener() {
            public void shellActivated(ShellEvent shellEvent) {}
            public void shellClosed(ShellEvent evt) { evt.doit = false; hide(); }
            public void shellDeactivated(ShellEvent shellEvent) {}
            public void shellDeiconified(ShellEvent shellEvent) {}
            public void shellIconified(ShellEvent shellEvent) {}
        });
        
        _themeRegistry.register(this);
        _translationRegistry.register(this);
        
        _shell.pack();
        Point sz = _tree.getControl().computeSize(SWT.DEFAULT, SWT.DEFAULT);
        sz.x += 50;
        sz.y += 200;
        _shell.setSize(sz.x, sz.y);
    }
    
    public void setFilter(SyndieURI filter) { 
        _tree.setFilter(filter);
        _tree.applyFilter();
    }
    
    public void show() { _shell.open(); }
    public void hide() { _shell.setVisible(false); }
    public void dispose() {
        _themeRegistry.unregister(this);
        _translationRegistry.unregister(this);
        if (!_shell.isDisposed()) _shell.dispose();
        _tree.dispose();
    }

    public void messageSelected(MessageTree tree, SyndieURI uri, boolean toView, boolean nodelay) {
        if (toView)
            _shell.setVisible(false);
        if (_listener != null)
            _listener.messageSelected(tree, uri, toView, nodelay);
    }

    public void filterApplied(MessageTree tree, SyndieURI searchURI) {
        if (_listener != null)
            _listener.filterApplied(tree, searchURI);
    }
    
    public void translate(TranslationRegistry registry) {
        _ok.setText(registry.getText("OK"));
        _cancel.setText(registry.getText("Cancel"));
    }
    
    
    public void applyTheme(Theme theme) {
        _ok.setFont(theme.BUTTON_FONT);
        _cancel.setFont(theme.BUTTON_FONT);
    }
}
