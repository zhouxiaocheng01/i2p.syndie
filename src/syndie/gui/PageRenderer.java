package syndie.gui;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import net.i2p.data.Hash;
import net.i2p.data.PrivateKey;
import net.i2p.data.SessionKey;
import net.i2p.data.SigningPrivateKey;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.*;

import syndie.Constants;
import syndie.data.MessageInfo;
import syndie.data.SyndieURI;
import syndie.db.CommandImpl;
import syndie.db.DBClient;
import syndie.db.NullUI;
import syndie.db.UI;
import syndie.html.HTMLStateBuilder;
import syndie.html.HTMLTag;
import syndie.util.StringUtil;

/**
 * Creates a new StyledText component for rendering pages.  Supports plain
 * text pages as well as html, offering a simple listener interface to receive
 * hover/menu/selection events for html elements.
 *
 * Used for both the MessageViewBody and the PageEditor preview.
 *
 */
class PageRenderer extends BaseComponent implements Themeable {
    private final DataCallback _dataCallback;
    private final Composite _parent;
    private final StyledText _text;
    private final boolean _showForumMenu;
    private PageRendererSource _source;
    private MessageInfo _msg;
    private int _page;
    private PageActionListener _listener;
    private ArrayList<Font> _fonts;
    private ArrayList<Color> _colors;
    private ArrayList<Integer> _imageIndexes;
    private ArrayList<Image> _images;
    private ArrayList<Integer> _liIndexes;
    private Color _bgColor;
    private Image _bgImage;
    
    private ArrayList<HTMLTag> _imageTags;
    private ArrayList<HTMLTag> _linkTags;
    
    private Menu _bodyMenu;
    private Menu _imageMenu;
    private Menu _linkMenu;
    private Menu _imageLinkMenu;
    
    private MenuItem _bodyViewForum;
    private MenuItem _bodyViewForumMetadata;
    private MenuItem _bodyBookmarkForum;
    private MenuItem _bodyViewAuthorForum;
    private MenuItem _bodyViewAuthorMetadata;
    private MenuItem _bodyBookmarkAuthor;
    private MenuItem _bodyMarkAsRead;
    private MenuItem _bodyMarkAsUnread;
    private MenuItem _bodyReplyToForum;
    private MenuItem _bodyReplyToAuthor;
    private MenuItem _bodyBanForum;
    private MenuItem _bodyBanAuthor;
    private MenuItem _bodyEnable;
    private MenuItem _bodyDisable;
    private MenuItem _bodyViewAsText;
    private MenuItem _bodyViewUnstyled;
    private MenuItem _bodyViewStyled;
    private MenuItem _bodySaveAll;
    private MenuItem _bodyDelete;
    private MenuItem _bodyCancel;
    
    private MenuItem _imgView;
    private MenuItem _imgSave;
    private MenuItem _imgSaveAll;
    private MenuItem _imgDisable;
    private MenuItem _imgEnable;
    private MenuItem _imgIgnoreAuthor;
    private MenuItem _imgIgnoreForum;
    
    private MenuItem _linkView;
    private MenuItem _linkBookmark;
    private MenuItem _linkImportReadKey;
    private MenuItem _linkImportPostKey;
    private MenuItem _linkImportManageKey;
    private MenuItem _linkImportReplyKey;
    private MenuItem _linkImportArchiveKey;
    
    private MenuItem _imgLinkViewLink;
    private MenuItem _imgLinkViewImg;
    private MenuItem _imgLinkSave;
    private MenuItem _imgLinkSaveAll;
    private MenuItem _imgLinkDisable;
    private MenuItem _imgLinkEnable;
    private MenuItem _imgLinkIgnoreAuthor;
    private MenuItem _imgLinkIgnoreForum;
    private MenuItem _imgLinkBookmarkLink;
    private MenuItem _imgLinkImportReadKey;
    private MenuItem _imgLinkImportPostKey;
    private MenuItem _imgLinkImportManageKey;
    private MenuItem _imgLinkImportReplyKey;
    private MenuItem _imgLinkImportArchiveKey;

    private boolean _enableImages;
    private boolean _enableRender;
    private boolean _viewAsText;
    private boolean _canViewAsHTML;
    
    private boolean _styled;
    
    private SyndieURI _currentEventURI;
    private HTMLTag _currentEventLinkTag;
    private Image _currentEventImage;
    private HTMLTag _currentEventImageTag;
    
    private int _viewSizeModifier;
    private int _charsPerLine;

    private boolean _disposed;
    /** top index to scroll to when rendered */
    private int _wantedTop;
    
    private long _lastMouseMove;
    
    private Caret _defaultCaret;
    
    /**
     * no scrollbars, no forum menu
     * @deprecated unused
     */
    public PageRenderer(DBClient client, UI ui, ThemeRegistry themes, TranslationRegistry trans, Composite parent, DataCallback callback) {
        this(client, ui, themes, trans, parent, false, false, callback);
    }

    public PageRenderer(DBClient client, UI ui, ThemeRegistry themes, TranslationRegistry trans, Composite parent,
                        boolean scrollbars, boolean showForumMenu, DataCallback callback) {
        super(client, ui, themes, trans);
        _parent = parent;
        _showForumMenu = showForumMenu;
        _dataCallback = callback;
        if (scrollbars)
            _text = new CustomStyledText(_ui, parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.MULTI | SWT.WRAP | SWT.READ_ONLY);
        else
            _text = new CustomStyledText(_ui, parent, /*SWT.H_SCROLL | SWT.V_SCROLL |*/ SWT.MULTI | SWT.WRAP | SWT.READ_ONLY);
        // by defining a caret, the styledtext will skip the potentially insanely expensive 
        // caret location positioning code (spending 12 minutes in org.eclipse.swt.internal.gtk.OS.gdk_pango_layout_get_clip_region)
        // this is the case on SWT3.3M4/M5, but if it ever is no longer the case,
        // this can be removed.
        _defaultCaret = new Caret(_text, SWT.NONE);
        _text.setCaret(_defaultCaret);
        // left top right bottom
        _text.setMargins(12, 8, 12, 8);
        //_defaultCaret.setVisible(false);
        _imageTags = new ArrayList();
        _linkTags = new ArrayList();
        
        _lastMouseMove = System.currentTimeMillis();
        
        _enableImages = true;
        _enableRender = true;
    
        buildMenus();
        ////pickBodyMenu();
        
        _text.setDoubleClickEnabled(true);
        _text.addSelectionListener(new SelectionListener() {
            public void widgetSelected(SelectionEvent selectionEvent) { _text.copy(); }
            public void widgetDefaultSelected(SelectionEvent selectionEvent) {}
        });
        _text.addMouseListener(new MouseListener() {
            public void mouseDoubleClick(MouseEvent mouseEvent) {}
            // TODO use single click to go direct to the link, not open the menu
            public void mouseDown(MouseEvent mouseEvent) { 
                pickMenu(mouseEvent.x, mouseEvent.y, true, mouseEvent.button != 1); 
            }
            public void mouseUp(MouseEvent mouseEvent) {}
        });
        _text.addMouseTrackListener(new MouseTrackListener() {
            public void mouseEnter(MouseEvent mouseEvent) { pickMenu(mouseEvent.x, mouseEvent.y, false); }
            public void mouseExit(MouseEvent mouseEvent) { 
                pickMenu(mouseEvent.x, mouseEvent.y, false);
            }
            public void mouseHover(MouseEvent mouseEvent) {
                if (_disposed) return;
                pickMenu(mouseEvent.x, mouseEvent.y, false);
                Point p = new Point(mouseEvent.x, mouseEvent.y);
                int off = -1;
                boolean link = false;
                try {
                    off = _text.getOffsetAtLocation(p);
                    HTMLTag linkTag = null;
                    StyleRange linkRange = null;
                    HTMLTag imgTag = null;
                    StyleRange imgRange = null;
                    for (int i = 0; i < _linkTags.size(); i++) {
                        HTMLTag tag = (HTMLTag)_linkTags.get(i);
                        if ( (off >= tag.startIndex) && (off <= tag.endIndex) ) {
                            StyleRange range = _text.getStyleRangeAtOffset(off);
                            linkTag = tag;
                            linkRange = range;
                            break;
                        }
                    }
                    for (int i = 0; i < _imageTags.size(); i++) {
                        HTMLTag tag = (HTMLTag)_imageTags.get(i);
                        if ( (off >= tag.startIndex) && (off <= tag.endIndex) ) {
                            StyleRange range = _text.getStyleRangeAtOffset(off);
                            imgRange = range;
                            imgTag = tag;
                            break;
                        }
                    }
                    if ( (imgTag != null) && (linkTag != null) ) {
                        hoverImageLink(imgRange, imgTag, linkRange, linkTag, off);
                    } else if (imgTag != null) {
                        hoverImage(imgRange, off, imgTag);
                    } else if (linkTag != null) {
                        hoverLink(linkRange, off, linkTag);
                        link = true;
                    } else {
                        _text.setToolTipText("");
                    }
                } catch (IllegalArgumentException iae) {
                    // no char at that point (why doesn't swt just return -1?)
                }
                //System.out.println("hoover [" + mouseEvent.x + " to " + mouseEvent.y + "] / " + off);
                if (!link)
                    _text.setCursor(null);
                _lastMouseMove = System.currentTimeMillis();
            }
        });
        
        _text.addMouseMoveListener(new MouseMoveListener() {
            public void mouseMove(MouseEvent mouseEvent) {
                if (_disposed) return;
                Point p = new Point(mouseEvent.x, mouseEvent.y);
                int off = -1;
                boolean link = false;
                try {
                    off = _text.getOffsetAtLocation(p);
                    HTMLTag linkTag = null;
                    StyleRange linkRange = null;
                    HTMLTag imgTag = null;
                    StyleRange imgRange = null;
                    for (int i = 0; i < _linkTags.size(); i++) {
                        HTMLTag tag = (HTMLTag)_linkTags.get(i);
                        if ( (off >= tag.startIndex) && (off <= tag.endIndex) ) {
                            StyleRange range = _text.getStyleRangeAtOffset(off);
                            linkTag = tag;
                            linkRange = range;
                            break;
                        }
                    }
                    if (linkTag != null)
                        link = true;
                } catch (IllegalArgumentException iae) {
                    // no char at that point (why doesn't swt just return -1?)
                }
                //System.out.println("hoover [" + mouseEvent.x + " to " + mouseEvent.y + "] / " + off);
                if (!link)
                    _text.setCursor(null);
                else
                    _text.setCursor(_text.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
                
                
                if (System.currentTimeMillis() - _lastMouseMove > 500)
                    setTooltip(_text, null);
                _lastMouseMove = System.currentTimeMillis();
            }
        });
        
        // draw the current image or bullet on the pane
        _text.addPaintObjectListener(new PaintObjectListener() {
            public void paintObject(PaintObjectEvent evt) {
                if (_disposed) return;
                GC gc = evt.gc;
                StyleRange range = evt.style;
                int start = range.start;
                if (_imageIndexes != null) {
                    for (int i = 0; i < _imageIndexes.size(); i++) {
                        int offset = ((Integer)_imageIndexes.get(i)).intValue();
                        if (start == offset) {
                            if (i >= _images.size()) return;
                            Image img = (Image)_images.get(i);
                            // Adjust origin by half the added margin
                            int xMargin = (range.metrics.width - img.getBounds().width) / 2;
                            int yMargin = (range.metrics.ascent - img.getBounds().height) / 2;
                            int x = evt.x + xMargin;
                            int y = evt.y + + yMargin + evt.ascent - range.metrics.ascent;
                            //System.out.println("Paint x=" + x + " y=" + y + " offset=" + offset + " image: " + img);
                            gc.drawImage(img, x, y);
                            return;
                        }
                    }
                }
            }
        });
        _text.addControlListener(new ControlListener() {
            public void controlMoved(ControlEvent controlEvent) {}
            public void controlResized(ControlEvent controlEvent) {
                //if ( (_msg != null) && (_enableRender) ) rerender();
            }
        });
        _text.addKeyListener(new KeyListener() {
            public void keyReleased(KeyEvent evt) { }
            public void keyPressed(KeyEvent evt) {
                //System.out.println("character pressed: " + (int)evt.character + " state: " + (int)evt.stateMask + " keycode: " + evt.keyCode);
                switch (evt.character) {
                    case '=': // ^=
                    case '+': // ^+
                        if ( (evt.stateMask & SWT.MOD1) != 0) {
                            _viewSizeModifier += 2;
                            rerender();
                        }
                        break;
                    case '_': // ^_
                    case '-': // ^-
                        if ( (evt.stateMask & SWT.MOD1) != 0) {
                            _viewSizeModifier -= 2;
                            rerender();
                        }
                        break;
                    case ' ':
                        pageDown(true);
                        break;
                    case 0x01: // ^A
                        _text.selectAll();
                        evt.doit = false;
                        break;
                    case 0x03: // ^C
                        if ( (evt.stateMask & SWT.MOD1) != 0) {
                            _text.copy();
                            evt.doit = false;
                        }
                        break;
                    case 0x18: // ^X for cut doesn't make sense in a page renderer, so just copy
                        if ( (evt.stateMask & SWT.MOD1) != 0) {
                            _text.copy();
                            evt.doit = false;
                        }
                        break;
                }
                //_browser.getUI().debugMessage("keyCode: " + evt.keyCode + " char=" + (int)evt.character + " state=" + evt.stateMask + " pgDown=" + SWT.PAGE_DOWN + "/" + ST.PAGE_DOWN + " pgUp=" + SWT.PAGE_UP + "/" + ST.PAGE_UP);
                if (evt.keyCode == SWT.PAGE_DOWN)
                    pageDown(false);
                else if (evt.keyCode == SWT.PAGE_UP)
                    pageUp(false);
            }
        });
        _themeRegistry.register(this);
    }
    private void pageDown(boolean fake) {
        ScrollBar bar = _text.getVerticalBar();
        if (bar != null) {
            int incr = bar.getPageIncrement();
            if (bar.getSelection() + 1 + incr >= bar.getMaximum()) {
                _ui.debugMessage("pageDown(" + fake + "): bar=" + bar + " sel=" + bar.getSelection() + " max=" + bar.getMaximum() + " min=" + bar.getMinimum() + " incr=" + bar.getIncrement() + "/" + bar.getPageIncrement());
                if (_listener != null)
                    _listener.nextPage();
            } else {
                _ui.debugMessage("pageDown(" + fake + "): bar=" + bar + " sel=" + bar.getSelection() + " max=" + bar.getMaximum() + " min=" + bar.getMinimum() + " incr=" + bar.getIncrement() + "/" + bar.getPageIncrement());
            }
        } else {
            _ui.debugMessage("pageDown(" + fake + "): bar=null");
        }
        if (fake)
            _text.invokeAction(ST.PAGE_DOWN);
    }
    private void pageUp(boolean fake) {
        ScrollBar bar = _text.getVerticalBar();
        if (bar != null) {
            int incr = bar.getPageIncrement();
            if (bar.getSelection() - 1 - incr <= bar.getMinimum()) {
                _ui.debugMessage("pageUp(" + fake + "): bar=" + bar + " sel=" + bar.getSelection() + " max=" + bar.getMaximum() + " min=" + bar.getMinimum() + " incr=" + bar.getIncrement() + "/" + bar.getPageIncrement());
                if (_listener != null)
                    _listener.prevPage();
            } else {
                _ui.debugMessage("pageUp(" + fake + "): bar=" + bar + " sel=" + bar.getSelection() + " max=" + bar.getMaximum() + " min=" + bar.getMinimum() + " incr=" + bar.getIncrement() + "/" + bar.getPageIncrement());
            }
        } else {
            _ui.debugMessage("pageUp(" + fake + "): bar=null");
        }
        if (fake)
            _text.invokeAction(ST.PAGE_UP);
    }
    public void setLayoutData(Object data) { _text.setLayoutData(data); }
    public void setListener(PageActionListener lsnr) { _listener = lsnr; }
    public Composite getComposite() { return _text; }
    public void setRender(boolean render) { _enableRender = render; }
    
    void addKeyListener(KeyListener lsnr) { _text.addKeyListener(lsnr); }
    void forceFocus() { _text.forceFocus(); }
    
    private void showNoPage() {
        _ui.debugMessage("show no page");
        _text.setVisible(false);
        _text.setText("");
        _text.setStyleRanges(null, null);
        setTop();
    }
    
    /**
     *  Set the top line, but if it's zero, set the top pixel,
     *  so we don't scroll past the margin.
     */
    private void setTop() {
        if (_wantedTop == 0)
            _text.setTopPixel(0);
        else
            _text.setTopIndex(_wantedTop);
    }

    public void renderPage(PageRendererSource src, SyndieURI uri) {
        _wantedTop = _text.getTopIndex();
        Hash chan = uri.getScope();
        if (chan == null) {
            showNoPage();
            src.renderComplete();
            return;
        }
        long chanId = src.getChannelId(chan);
        if (chanId < 0) {
            showNoPage();
            src.renderComplete();
            return;
        }
        MessageInfo msg = src.getMessage(chanId, uri.getMessageId());
        if (msg == null) {
            showNoPage();
            src.renderComplete();
            return;
        }
        //if (msg.getPassphrasePrompt() != null) {
        //    showNoPage();
        //    return;
        //}
        Long page = null;
        page = uri.getLong("page");
        if (page != null) {
            if ( (page.longValue() > msg.getPageCount()) || (page.longValue() <= 0) )
                page = Long.valueOf(1);
        } else {
            page = Long.valueOf(1);
        }
        renderPage(src, msg, page.intValue());
    }
    private void renderPage(PageRendererSource src, MessageInfo msg, int pageNum) {
        _source = src;
        _msg = msg;
        _page = pageNum;
        _styled = (_bodyViewStyled == null ? true : _bodyViewStyled.getSelection());
        //System.out.println("rendering "+ msg + ": " + pageNum);
        _text.setCursor(Display.getDefault().getSystemCursor(SWT.CURSOR_WAIT));
        //_text.setRedraw(false);
        _ui.debugMessage("Enqueue html render");
        PageRendererThread.enqueue(this);
    }

    /** called from the PageRendererThread - note that this thread cannot update SWT components! */
    void threadedRender() {
        long before = System.currentTimeMillis();
        if (_msg == null) {
            renderText(null);
            _source.renderComplete();
            return;
        }
        String cfg = _source.getMessagePageConfig(_msg.getInternalId(), _page);
        String body = _source.getMessagePageData(_msg.getInternalId(), _page);
        //_ui.debugMessage("threaded render: body=[" + body + "] cfg=[" + cfg + "]");
        if ( (cfg == null) || (body == null) ) {
            //System.out.println("threaded render had no body or config: " + _msg.getInternalId() + ", page " + _page + ", body? " + (body != null) + " cfg? " + (cfg != null));
            renderText(null);
            _source.renderComplete();
            return;
        }
        Properties props = new Properties();
        CommandImpl.parseProps(cfg, props);
        String mimeType = props.getProperty(Constants.MSG_PAGE_CONTENT_TYPE, "text/plain");
        _canViewAsHTML = "text/html".equalsIgnoreCase(mimeType) || "text/xhtml".equalsIgnoreCase(mimeType);
        if (_canViewAsHTML && !_viewAsText) {
            renderHTML(body);
        } else {
            renderText(body);
        }
        long after = System.currentTimeMillis();
        _ui.debugMessage("threaded page render took " + (after-before));
        _source.renderComplete();
    }

    private void renderText(final String body) {
        if (_text.isDisposed()) {
            _ui.errorMessage("render after dispose?", new Exception("source"));
            return;
        }
        _text.getDisplay().asyncExec(new Runnable() {
            public void run() {
                if (_text.isDisposed()) return;
                _text.setRedraw(false);
                disposeFonts();
                disposeColors();
                disposeImages();
        
                _text.setStyleRanges(null, null);
                if (body != null) {
                    _text.setText(body);
                    StyleRange range = new StyleRange(0, body.length(), null, null);
                    range.font = _themeRegistry.getTheme().MONOSPACE_FONT;
                    _text.setStyleRange(range);
                } else {
                    _text.setText("");
                }
                _text.setBackgroundImage(null); // make this explicit, for toggling
                _text.setBackground(null);

                _text.setVisible(true);
                
                setTop();
                
                _text.setRedraw(true);
                _text.setCursor(null);
                if (body == null)
                    _text.setEnabled(false);
                else
                    _text.setEnabled(true);
            }
        });
    }
    private int getCharsPerLine() {
        if (false) {
            // have the HTMLStateBuilder inject fake line wrapping, even though
            // the wrapping won't be right all of the time.  this lets wrapped
            // lines have the right indentation.  however, it can cause problems
            // for bullet points, as each line is given a bullet
            _text.getDisplay().syncExec(new Runnable() {
                public void run() {
                    // problem: this uses the default font, not the themed font.  can we get around this?
                    GC gc = new GC(_text);
                    FontMetrics metrics = gc.getFontMetrics();
                    int charWidth = metrics.getAverageCharWidth();
                    gc.dispose();
                    int paneWidth = _text.getBounds().width;
                    int w = _text.getClientArea().width;
                    int ww = _parent.getClientArea().width;
                    //if (paneWidth > 800) paneWidth = 800;
                    //else if (paneWidth < 100) paneWidth = 100;
                    _charsPerLine = paneWidth / (charWidth == 0 ? 12 : charWidth);
                    _ui.debugMessage("max chars per line: " + _charsPerLine + " pane width: " + paneWidth + "/" + ww + "/" + w + " charWidth: " + charWidth);
                }
            });
        }
        return _charsPerLine;
    }
    private void renderHTML(String html) {
        _ui.debugMessage("Beginning renderHTML");
        if (_text.isDisposed()) return;
        _text.getDisplay().syncExec(new Runnable() {
            public void run() {
                disposeFonts();
                disposeColors();
                disposeImages();
            }
        });
        _ui.debugMessage("renderHTML: old stuff disposed");

        _charsPerLine = getCharsPerLine();
        
        final HTMLStateBuilder builder = new HTMLStateBuilder(_ui, html, _charsPerLine);
        builder.buildState();
        _ui.debugMessage("renderHTML: state built");
        final String rawText = builder.getAsText();
        final HTMLStyleBuilder sbuilder = new HTMLStyleBuilder(_ui, _source, builder.getTags(), rawText, _msg, _enableImages, _styled);
        final String text = HTMLStateBuilder.stripPlaceholders(rawText);
        
        _ui.debugMessage("renderHTML: building styles");
        //todo: do this in two parts, once in the current thread, another in the swt thread
        sbuilder.buildStyles(_viewSizeModifier);
        _ui.debugMessage("renderHTML: styles built");
        final ArrayList fonts = sbuilder.getFonts();
        final ArrayList colors = sbuilder.getCustomColors();
        // also need to get the ranges for images/internal page links/internal attachments/links/etc
        // so that the listeners registered in the constructor can do their thing
        final ArrayList imageIndexes = sbuilder.getImageIndexes();
        final ArrayList liIndexes = sbuilder.getListItemIndexes();
        final ArrayList images = sbuilder.getImages();
        if (images.size() != imageIndexes.size()) {
            throw new RuntimeException("images: " + images + " imageIndexes: " + imageIndexes);
        }
        // the _imageIndexes/_images contain the image for the linkEnd values, but
        // we may want to keep track of them separately for menu handling
        //Collection linkEndIndexes = sbuilder.getLinkEndIndexes();
        
        final ArrayList linkTags = sbuilder.getLinkTags();
        final ArrayList imageTags = sbuilder.getImageTags();
        
        if (_text.isDisposed()) return;
        _ui.debugMessage("before syncExec to write on the styledText");
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                long start = System.currentTimeMillis();
                _fonts = fonts;
                _colors = colors;
                _imageIndexes = imageIndexes;
                _liIndexes = liIndexes;
                _images = images;
                _linkTags = linkTags;
                _imageTags = imageTags;

                if (_text.isDisposed()) return;
                
                _text.setRedraw(false);
                _text.setEnabled(true);
                long before = System.currentTimeMillis();
                _text.setText(text);
                long after = System.currentTimeMillis();
                _ui.debugMessage("syncExec to write on the styledText: text written in " + (after-before) + ": list indexes: " + _liIndexes);
                StyleRange ranges[] = sbuilder.getStyleRanges();
                long beforeSet = System.currentTimeMillis();
                _text.setStyleRanges(ranges);
                long afterSet = System.currentTimeMillis();
                _ui.debugMessage("syncExec to write on the styledText: ranges set w/ " + ranges.length + " in " + (afterSet-beforeSet));
                before = System.currentTimeMillis();
                setLineProperties(builder, sbuilder);
                after = System.currentTimeMillis();
                _ui.debugMessage("syncExec to write on the styledText: line props set after " + (after-before));

                _bgImage = sbuilder.getBackgroundImage();
                if (_styled && _bgImage != null) {
                    _text.setBackgroundImage(_bgImage);
                } else {
                    _text.setBackgroundImage(null);
                    //_text.setBackgroundMode(SWT.INHERIT_DEFAULT); // use the container's background
                }

                _bgColor = sbuilder.getBackgroundColor();
                if (_styled && _bgColor != null)
                    _text.setBackground(_bgColor);
                else
                //    _text.setBackground(ColorUtil.getColor("white")); //null);
                    _text.setBackground(null); // perhaps this'll work properly sans the INHERIT_DEFAULT above
                
                setTop();
                
                _text.setVisible(true);
                _text.setRedraw(true);
                _text.setCursor(null);
                long end = System.currentTimeMillis();
                _ui.debugMessage("syncExec to write on the styledText: visible, redraw, cursor configured in " + (end-start));
            }
        });
    }
    
    /**
     * markup on a char by char level is done, but now handle the markup on a line-by-line
     * level, with indents, coloring, bullets, etc
     */
    private void setLineProperties(HTMLStateBuilder stateBuilder, HTMLStyleBuilder styleBuilder) {
        long prep = System.currentTimeMillis();
        int lines = _text.getLineCount();
        /*
        _text.setLineAlignment(0, lines, SWT.LEFT);
        _text.setLineBackground(0, lines, null);
        _text.setLineBullet(0, lines, null);
        _text.setLineIndent(0, lines, 0);
        _text.setLineJustify(0, lines, false);
         */
        
        // this is only an estimate used for indentation, so the fact that it doesn't
        // actually take into account the actual fonts used can probably be overlooked
        int charWidth = -1;
        GC gc = new GC(_text);
        FontMetrics metrics = gc.getFontMetrics();
        charWidth = metrics.getAverageCharWidth();
        gc.dispose();
        
        long bulletTime = 0;
        long indentTime = 0;
        long alignmentTime = 0;
        int alignmentMods = 0;
        int prevAlignment = -1;
        int sequentialAligned = 0;

        ArrayList lineTags = new ArrayList(16);
        HTMLTag stateTags[] = (HTMLTag[])stateBuilder.getTags().toArray(new HTMLTag[0]);
        int stateTagCount = stateTags.length;
        
        /*
        for (int i = 0; i < stateTags.length; i++) {
            if ("li".equals(stateTags[i].name)) {
                _browser.getUI().debugMessage("li tag: " + stateTags[i].toString());
                _browser.getUI().debugMessage("li tag body: " + _text.getText(stateTags[i].startIndex, stateTags[i].endIndex) + "\n\n");
            }
        }
         */
        
        int bodySize = _text.getCharCount();
        Map<HTMLTag, Bullet> bulletLists = new HashMap();
        long times[] = new long[lines];
        long timesOff[] = new long[lines];
        long timesGetTags[] = new long[lines];
        long timesFindAlign[] = new long[lines];
        long timesFindList[] = new long[lines];
        long timesPrepare[] = new long[lines];
        long endPrep = System.currentTimeMillis();
        for (int line = 0; line < lines; line++) {
            times[line] = System.currentTimeMillis();
            int lineStart = _text.getOffsetAtLine(line);
            int lineEnd = -1;
            if (line + 1 == lines)
                lineEnd = bodySize;
            else
                lineEnd = _text.getOffsetAtLine(line+1)-1;
            timesOff[line] = System.currentTimeMillis();
            //_browser.getUI().debugMessage("line " + line + " goes from " + lineStart + " to " + lineEnd);
            
            int alignment = SWT.LEFT;
            
            // now get the tags applicable to [lineStart,lineEnd]
            //boolean liRowFound = false;
            for (int i = 0; i < stateTagCount; i++) {
                HTMLTag tag = stateTags[i];
                if ( (tag.startIndex <= lineEnd) && (tag.endIndex >= lineStart) ) {
                    //if (tag.name.equals("li") && (tag.startIndex >= lineStart) )
                    //    liRowFound = true;
                    lineTags.add(tag);
                }
                //else if (tag.endIndex > lineStart)
                //    break; // the stateTags are ordered with earliest end first
                //!! which means that you can't break there jrandom.
            }
            //if (liRowFound) {
            //    _browser.getUI().debugMessage("tag for line " + line + ": " + lineTags);
            //    _browser.getUI().debugMessage("content on that line: " + _text.getText(lineStart, lineEnd));
            //}
            //ArrayList tags = getTags(stateBuilder, styleBuilder, lineStart, lineEnd);
            timesGetTags[line] = System.currentTimeMillis();
            if (HTMLStyleBuilder.containsTag(lineTags, "pre")) {
                // if they have pre, do no formatting
            } else {
                // look for alignment attributes
                for (int i = 0; i < lineTags.size(); i++) {
                    HTMLTag tag = (HTMLTag)lineTags.get(i);
                    String align = tag.getAttribValue("align");
                    if (align != null) {
                        if ("left".equalsIgnoreCase(align))
                            alignment = SWT.LEFT;
                        else if ("center".equalsIgnoreCase(align))
                            alignment = SWT.CENTER;
                        else if ("right".equalsIgnoreCase(align))
                            alignment = SWT.RIGHT;
                        else
                            continue;
                        break; // left|center|right
                    }
                }
                // look for center tags
                if (HTMLStyleBuilder.containsTag(lineTags, "center"))
                    alignment = SWT.CENTER;
            }
            
            timesFindAlign[line] = System.currentTimeMillis();
            
            boolean bulletOrdered = false;
            int olLevel = 0;
            int ulLevel = 0;
            
            Bullet bullet = null;
            boolean liFound = false;
            int indentLevel = 0;
            // look for li tags, and indent $x times the nesting layer
            for (int i = 0; i < lineTags.size(); i++) {
                HTMLTag tag = (HTMLTag)lineTags.get(i);
                if ("li".equals(tag.name)) {
                    indentLevel++;
                    // we only want to put a bullet point on the first line of
                    // a potentially multiline list item
                    if (!tag.wasConsumed()) {
                        liFound = true;
                        tag.consume();
                    }
                } else if ("ol".equals(tag.name) && liFound) {
                    if ( (olLevel == 0) && (ulLevel == 0) ) {
                        bulletOrdered = true;
                        bullet = bulletLists.get(tag);
                        if (bullet == null) {
                            StyleRange bulletRange = new StyleRange();
                            bulletRange.metrics = new GlyphMetrics(0, 0, 0);
                            bullet = new Bullet(ST.BULLET_NUMBER | ST.BULLET_TEXT, bulletRange);
                            bullet.text = ")";
                            bulletLists.put(tag, bullet);
                        }
                    }
                    olLevel++;
                } else if ("ul".equals(tag.name) && liFound) {
                    if ( (olLevel == 0) && (ulLevel == 0) ) {
                        bulletOrdered = false;
                        bullet = bulletLists.get(tag);
                        if (bullet == null) {
                            StyleRange bulletRange = new StyleRange();
                            bulletRange.metrics = new GlyphMetrics(0, 0, 0);
                            bullet = new Bullet(ST.BULLET_DOT, bulletRange);
                            bulletLists.put(tag, bullet);
                        }
                    }
                    ulLevel++;
                }
            }
            
            timesFindList[line] = System.currentTimeMillis();
            
            //if (indentLevel > 0)
            //    System.out.println("indent level: " + indentLevel + " bullet: " + bullet + " ulLevel: " + ulLevel + " olLevel: " + olLevel);
            
            boolean quoteFound = false;
            // look for <quote> tags, and indent $x times the nesting layer
            for (int i = 0; i < lineTags.size(); i++) {
                HTMLTag tag = (HTMLTag)lineTags.get(i);
                if ("quote".equals(tag.name)) {
                    indentLevel++;
                    quoteFound = true;
                }
            }
            
            // look for <dd/dt> tags, and indent $x times the nesting layer
            /*
            if (HTMLStyleBuilder.containsTag(lineTags, "dd"))
                indentLevel += 2;
            if (HTMLStyleBuilder.containsTag(lineTags, "dt"))
                indentLevel++;
             */
            //boolean defFound = false;
            for (int i = 0; i < lineTags.size(); i++) {
                HTMLTag tag = (HTMLTag)lineTags.get(i);
                if ("dd".equals(tag.name)) {
                    indentLevel += 2;
                    //defFound = true;
                }
                if ("dt".equals(tag.name)) {
                    indentLevel++;
                    //defFound = true;
                }
            }
            //if (defFound)
            //    _browser.getUI().debugMessage("def found on line " + line + ", indentLevel: " + indentLevel + " tags: " + lineTags + "\n content: " + _text.getText(lineStart, lineEnd));

            timesPrepare[line] = System.currentTimeMillis();
            
            // we could optimize the line settings to deal with sequential lines w/ == settings,
            // but its not worth it atm
            long t1 = System.currentTimeMillis();
            if (alignment != SWT.LEFT)
                _text.setLineAlignment(line, 1, alignment);
            long t2 = System.currentTimeMillis();
            alignmentTime += (t2-t1);
            if (prevAlignment != alignment) {
                prevAlignment = alignment;
                //sequentialAligned = 0;
            } else {
                sequentialAligned++;
            }

            if (bullet != null) {
                int width = bullet.style.metrics.width;
                if (width <= 0)
                    bullet.style.metrics.width = indentLevel * 4 * charWidth;
                _text.setLineBullet(line, 1, bullet);
                //_text.setLineIndent(line, 1, indentLevel * 4 * charWidth);
                long t3 = System.currentTimeMillis();
                bulletTime += (t3-t2);
            } else if (indentLevel > 0) {
                _text.setLineIndent(line, 1, indentLevel * 4 * charWidth);
                long t3 = System.currentTimeMillis();
                indentTime += (t3-t2);
            }
            
            lineTags.clear();
        }

        long timesOffTot = 0;
        long timesGetTagsTot = 0;
        long timesFindAlignTot = 0;
        long timesFindListTot = 0;
        for (int i = 0; i < lines; i++) {
            timesOffTot += timesOff[i]-times[i];
            timesGetTagsTot += timesGetTags[i]-timesOff[i];
            timesFindAlignTot += timesFindAlign[i]-timesGetTags[i];
            timesFindListTot += timesFindList[i]-timesFindAlign[i];
        }
        /*
        _browser.getUI().debugMessage("line style: alignment: " + alignmentTime + ", bullets: " + bulletTime 
                                      + " indent: " + indentTime 
                                      //+ " sequential: " + sequentialAligned 
                                      + " prep: " + (endPrep-prep) + " timesOff: " + timesOffTot
                                      + " timesGetTags: " + timesGetTagsTot
                                      + " timesAlign: " + timesFindAlignTot 
                                      + " timesList: " + timesFindListTot);
         */
    }
    
    public void dispose() {
        _disposed = true;
        disposeFonts();
        disposeColors();
        disposeImages();
        _defaultCaret.dispose();
        _themeRegistry.unregister(this);
    }
    
    private void disposeFonts() {
        if (_fonts != null) {
            for (int i = 0; i < _fonts.size(); i++) {
                Font f = (Font)_fonts.get(i);
                if (!f.isDisposed())
                    f.dispose();
            }
            _fonts = null;
        }
    }
    private void disposeColors() {
        if (_colors != null) {
            for (int i = 0; i < _colors.size(); i++) {
                Color c = (Color)_colors.get(i);
                if ( (!c.isDisposed()) && (!ColorUtil.isSystemColor(c)) )
                    c.dispose();
            }
            _colors = null;
        }
        if ( (_bgColor != null) && (!_bgColor.isDisposed()) && (!ColorUtil.isSystemColor(_bgColor)))
            _bgColor.dispose();
        _bgColor = null;
    }
    private void disposeImages() {
        if ( (_bgImage != null) && (!_bgImage.isDisposed()) )
            _bgImage.dispose();
        _bgImage = null;
        if (_images != null) {
            for (int i = 0; i < _images.size(); i++) {
                Image img = (Image)_images.get(i);
                ImageUtil.dispose(img);
                //if (img == ImageUtil.ICON_IMAGE_UNKNOWN) continue;
                //if (img == ImageUtil.ICON_LINK_END) continue;
                //if (ColorUtil.isSystemColorSwatch(img)) continue;
            }
            _images.clear();
        }
    }
    
    public MessageInfo getCurrentMessage() { return _msg; }
    public int getCurrentPage() { return _page; }
    //public DBClient getCurrentClient() { return _client; }

    private void pickMenu(int x, int y, boolean showMenu) { pickMenu(x, y, showMenu, false); }
    private void pickMenu(int x, int y, boolean showMenu, boolean isRightClick) {
        if (_disposed) return;
        //_browser.getUI().debugMessage("menu is visible? " + _text.getMenu().isVisible());
        Point p = new Point(x, y);
        int off = -1;
        try {
            off = _text.getOffsetAtLocation(p);
            HTMLTag linkTag = null;
            HTMLTag imgTag = null;
            for (int i = 0; i < _linkTags.size(); i++) {
                HTMLTag tag = (HTMLTag)_linkTags.get(i);
                if ( (off >= tag.startIndex) && (off <= tag.endIndex) ) {
                    linkTag = tag;
                    break;
                }
            }
            for (int i = 0; i < _imageTags.size(); i++) {
                HTMLTag tag = (HTMLTag)_imageTags.get(i);
                if ( (off >= tag.startIndex) && (off <= tag.endIndex) ) {
                    imgTag = tag;
                    break;
                }
            }
            Menu m = _text.getMenu();
            if ( (imgTag != null) && (linkTag != null) ) {
                if (m != null) m.setVisible(false);
                pickImageLinkMenu(linkTag, imgTag);
                if (showMenu && isRightClick) _text.getMenu().setVisible(true);
                return;
            } else if (linkTag != null) {
                if (m != null) m.setVisible(false);
                pickLinkMenu(linkTag);
                if (showMenu) _text.getMenu().setVisible(true);
                return;
            } else if (imgTag != null) {
                if (m != null) m.setVisible(false);
                pickImageMenu(imgTag);
                if (showMenu && isRightClick) _text.getMenu().setVisible(true);
                return;
            }
        } catch (IllegalArgumentException iae) {
            // no char at that point (why doesn't swt just return -1?)
        }
        pickBodyMenu();
        //_text.getMenu().setVisible(true);
    }
    
    private void pickImageLinkMenu(HTMLTag linkTag, HTMLTag imgTag) {
        if (_imageLinkMenu == null)
            buildImageLinkMenu();
        _text.setMenu(_imageLinkMenu);
        _ui.debugMessage("pickImageLinkMenu: " + imgTag);
        SyndieURI uri = null;
        if (linkTag != null)
            uri = HTMLStyleBuilder.getURI(linkTag.getAttribValue("href"), _msg);
        if ( (_msg == null) || (linkTag == null) || (uri == null) ) {
            _imgLinkBookmarkLink.setEnabled(false);
            _imgLinkDisable.setEnabled(false);
            _imgLinkEnable.setEnabled(false);
            if (_showForumMenu) {
                _imgLinkIgnoreAuthor.setEnabled(false);
                _imgLinkIgnoreForum.setEnabled(false);
                _imgLinkImportArchiveKey.setEnabled(false);
                _imgLinkImportManageKey.setEnabled(false);
                _imgLinkImportPostKey.setEnabled(false);
                _imgLinkImportReadKey.setEnabled(false);
                _imgLinkImportReplyKey.setEnabled(false);
            }
            _imgLinkSave.setEnabled(false);
            _imgLinkSaveAll.setEnabled(false);
            _imgLinkViewImg.setEnabled(false);
            _imgLinkViewLink.setEnabled(false);
            _currentEventURI = null;
            _currentEventLinkTag = null;
            _currentEventImage = null;
        } else {
            if (uri.isChannel()) {
                _imgLinkViewLink.setEnabled(true);
                _imgLinkBookmarkLink.setEnabled(true);
            } else if (uri.isArchive()) {
                _imgLinkViewLink.setEnabled(true);
                _imgLinkBookmarkLink.setEnabled(true);
            } else {
                _imgLinkViewLink.setEnabled(true);
                _imgLinkBookmarkLink.setEnabled(false);
            }
            
            _currentEventURI = uri;
            _currentEventLinkTag = linkTag;
            _currentEventImage = null;
            if (imgTag != null) {
                for (int i = 0; i < _imageIndexes.size(); i++) {
                    Integer idx = (Integer)_imageIndexes.get(i);
                    if (idx.intValue() == imgTag.startIndex) {
                        if (_images.size() <= 0) return; // disposing
                        _currentEventImage = (Image)_images.get(i);
                        _currentEventImageTag = imgTag;
                        break;
                    }
                }
            }
            
            _imgLinkDisable.setEnabled(_enableImages);
            _imgLinkEnable.setEnabled(!_enableImages);
            
            if (_showForumMenu) {
                long targetId = _msg.getTargetChannelId();
                long authorId = _msg.getAuthorChannelId();
                if ( (targetId == authorId) || (authorId < 0) ) {
                    _imgLinkIgnoreAuthor.setEnabled(false);
                } else {
                    _imgLinkIgnoreAuthor.setEnabled(true);
                }

                _imgLinkIgnoreForum.setEnabled(true);
                _imgLinkImportArchiveKey.setEnabled(uri.getArchiveKey() != null);
                _imgLinkImportManageKey.setEnabled(uri.getManageKey() != null);
                _imgLinkImportPostKey.setEnabled(uri.getPostKey() != null);
                _imgLinkImportReadKey.setEnabled(uri.getReadKey() != null);
                _imgLinkImportReplyKey.setEnabled(uri.getReplyKey() != null);
            }
            _imgLinkSave.setEnabled(true);
            _imgLinkSaveAll.setEnabled(true);
            _imgLinkViewImg.setEnabled(true);
        }
    }
    
    private void pickLinkMenu(HTMLTag linkTag) {
        if (_linkMenu == null)
            buildLinkMenu();
        _text.setMenu(_linkMenu);
        _ui.debugMessage("pickLinkMenu: " + linkTag);
        SyndieURI uri = null;
        if (linkTag != null)
            uri = HTMLStyleBuilder.getURI(linkTag.getAttribValue("href"), _msg);
        if ( (_msg == null) || (linkTag == null) || (uri == null) ) {
            _linkView.setEnabled(false);
            _linkBookmark.setEnabled(false);
            if (_showForumMenu) {
                _linkImportArchiveKey.setEnabled(false);
                _linkImportManageKey.setEnabled(false);
                _linkImportPostKey.setEnabled(false);
                _linkImportReadKey.setEnabled(false);
                _linkImportReplyKey.setEnabled(false);
            }
            _currentEventURI = null;
            _currentEventLinkTag = null;
            _currentEventImage = null;
        } else {
            if (uri.isChannel()) {
                _linkView.setEnabled(true);
                _linkBookmark.setEnabled(true);
            } else if (uri.isArchive()) {
                _linkView.setEnabled(true);
                _linkBookmark.setEnabled(true);
            } else {
                _linkView.setEnabled(true);
                _linkBookmark.setEnabled(false);
            }
            
            _currentEventURI = uri;
            _currentEventLinkTag = linkTag;
            _currentEventImage = null;
            
            if (_showForumMenu) {
                _linkImportManageKey.setEnabled(uri.getManageKey() != null);
                _linkImportPostKey.setEnabled(uri.getPostKey() != null);
                _linkImportReadKey.setEnabled(uri.getReadKey() != null);
                _linkImportReplyKey.setEnabled(uri.getReplyKey() != null);
                _linkImportArchiveKey.setEnabled(uri.getArchiveKey() != null);
            }
        }
    }
    
    /**
     *  _showForumMenu controls whether to show author/forum choices
     */
    private void pickBodyMenu() {
        if (_bodyMenu == null)
            buildBodyMenu();
        _text.setMenu(_bodyMenu);
        //_browser.getUI().debugMessage("pickBodyMenu");
            
        _currentEventURI = null;
        _currentEventLinkTag = null;
        _currentEventImage = null;
            
        if (_msg == null) {
            if (_showForumMenu) {
                _bodyBanAuthor.setEnabled(false);
                _bodyBanForum.setEnabled(false);
                _bodyBookmarkAuthor.setEnabled(false);
                _bodyBookmarkForum.setEnabled(false);
                _bodyReplyToForum.setEnabled(false);
                _bodyReplyToAuthor.setEnabled(false);
                _bodyViewAuthorForum.setEnabled(false);
                _bodyViewAuthorMetadata.setEnabled(false);
                _bodyViewForum.setEnabled(false);
                _bodyViewForumMetadata.setEnabled(false);
                _bodySaveAll.setEnabled(false);
            }
            _bodyEnable.setEnabled(false);
            _bodyDisable.setEnabled(false);
            //_bodyViewAsText.setEnabled(false);
            _bodyViewUnstyled.setEnabled(false);
            _bodyViewStyled.setEnabled(false);
        } else {
            _bodyDisable.setEnabled(_enableImages);
            _bodyEnable.setEnabled(!_enableImages);
            _bodyViewAsText.setSelection(_viewAsText || !_canViewAsHTML);
            _bodyViewAsText.setEnabled(true);
            _bodyViewUnstyled.setEnabled(_canViewAsHTML);
            _bodyViewStyled.setEnabled(_canViewAsHTML);
            
            if (_showForumMenu) {
                _bodySaveAll.setEnabled(true);
                long targetId = _msg.getTargetChannelId();
                long authorId = _msg.getAuthorChannelId();
                boolean isPM = _msg.getWasPrivate();
                // If author == target, so no need for a separate set of author commands
                boolean hasAuthor = targetId != authorId && authorId >= 0;
                _bodyBanAuthor.setEnabled(hasAuthor);
                _bodyBookmarkAuthor.setEnabled(hasAuthor);
                _bodyViewAuthorForum.setEnabled(hasAuthor);
                _bodyViewAuthorMetadata.setEnabled(hasAuthor);
                _bodyReplyToForum.setEnabled(!isPM);
                _bodyReplyToAuthor.setEnabled(true);
                _bodyBanForum.setEnabled(true);
                _bodyBookmarkForum.setEnabled(true);
                _bodyViewForum.setEnabled(true);
                _bodyViewForumMetadata.setEnabled(true);
            }
        }
    }
    
    private void pickImageMenu(HTMLTag imgTag) {
        //_browser.getUI().debugMessage("pickImageMenu: " + imgTag);
        if (_imageMenu == null)
            buildImageMenu();
        _text.setMenu(_imageMenu);
            
        _currentEventURI = null;
        _currentEventLinkTag = null;
        
        _currentEventImage = null;
        if (imgTag != null) {
            for (int i = 0; i < _imageIndexes.size(); i++) {
                Integer idx = (Integer)_imageIndexes.get(i);
                if (idx.intValue() == imgTag.startIndex) {
                    if (_images.size() <= i) return; // disposing
                    _currentEventImage = (Image)_images.get(i);
                    _currentEventImageTag = imgTag;
                    break;
                }
            }
        }
        
        if (_msg == null) {
            _imgDisable.setEnabled(false);
            _imgEnable.setEnabled(false);
            if (_showForumMenu) {
                _imgIgnoreAuthor.setEnabled(false);
                _imgIgnoreForum.setEnabled(false);
            }
            /*_imgSave.setEnabled(false);
            _imgSaveAll.setEnabled(false);
            _imgView.setEnabled(false);
             */
        } else {
            _imgDisable.setEnabled(_enableImages);
            _imgEnable.setEnabled(!_enableImages);
            /*_imgSave.setEnabled(true);
            _imgSaveAll.setEnabled(true);
            _imgView.setEnabled(true);
             */
            if (_showForumMenu) {
                _imgIgnoreForum.setEnabled(true);

                long targetId = _msg.getTargetChannelId();
                long authorId = _msg.getAuthorChannelId();
                if ( (targetId == authorId) || (authorId < 0) ) {
                    _imgIgnoreAuthor.setEnabled(false);
                } else {
                    _imgIgnoreAuthor.setEnabled(true);
                }
            }
        }
    }
    
    private void hoverLink(StyleRange range, int offset, HTMLTag tag) {
        //System.out.println("Hover over link @ " + offset + ": " + tag);
        String href = tag.getAttribValue("href");
        String title = tag.getAttribValue("title");
        StringBuilder buf = new StringBuilder();
        if (title != null) {
            buf.append(CommandImpl.strip(title));
            if (href != null)
                buf.append('\n');
        }
        if (href != null)
            buf.append(CommandImpl.strip(href));
        setTooltip(_text, buf.toString());
    }

    private static void setTooltip(StyledText text, String val) {
        if ( (val == null) || (val.length() == 0) ) {
            text.setToolTipText(null);
        } else {
            String existing = text.getToolTipText();
            if ( (existing == null) || (!existing.equals(val)))
                text.setToolTipText(val);
            else
                text.setToolTipText(null);
        }
    }

    private void hoverImage(StyleRange range, int offset, HTMLTag tag) {
        //System.out.println("Hover over image @ " + offset + ": " + tag);
        String alt = tag.getAttribValue("alt");
        String src = tag.getAttribValue("src");
        StringBuilder buf = new StringBuilder();
        if (alt != null) {
            buf.append(CommandImpl.strip(alt));
            if (src != null)
                buf.append('\n');
        }
        if (src != null)
            buf.append(CommandImpl.strip(src));
        setTooltip(_text, buf.toString());
    }

    private void hoverImageLink(StyleRange imgRange, HTMLTag imgTag, StyleRange linkRange, HTMLTag linkTag, int off) {
        StringBuilder buf = new StringBuilder();
        String alt = imgTag.getAttribValue("alt");
        String src = imgTag.getAttribValue("src");
        String href = linkTag.getAttribValue("href");
        String title = linkTag.getAttribValue("title");
        
        if (alt != null) {
            buf.append(CommandImpl.strip(alt));
            if (src != null)
                buf.append('\n');
        }
        if (src != null)
            buf.append(CommandImpl.strip(src));
        
        if ( (alt != null) || (src != null))
            buf.append('\n');
        
        if (title != null) {
            buf.append(CommandImpl.strip(title));
            if (href != null)
                buf.append('\n');
        }
        if (href != null)
            buf.append(CommandImpl.strip(href));

        setTooltip(_text, buf.toString());
    }
    
    
    private void buildMenus() {
        //buildBodyMenu();
        //buildLinkMenu();
        //buildImageMenu();
        //buildImageLinkMenu();
    }

    private void toggleImages() {
        boolean old = _enableImages;
        _enableImages = !old;
        if (_imageLinkMenu != null) {
            _imgLinkDisable.setEnabled(_enableImages);
            _imgLinkEnable.setEnabled(!_enableImages);
        }
        if (_imageMenu != null) {
            _imgDisable.setEnabled(_enableImages);
            _imgEnable.setEnabled(!_enableImages);
        }
        _bodyDisable.setEnabled(_enableImages);
        _bodyEnable.setEnabled(!_enableImages);
        rerender();
    }
    
    /** take care that only one of these 3 calls rerender() */
    private void toggleViewAsText() {
        if (_bodyViewAsText.getSelection()) {
            _viewAsText = true;
            rerender();
        }
    }

    private void toggleViewStyled() {
        if (_bodyViewStyled.getSelection()) {
            _viewAsText = false;
            rerender();
        }
    }

    /** same as styled */
    private void toggleViewUnstyled() { 
        if (_bodyViewUnstyled.getSelection()) {
            _viewAsText = false;
            rerender();
        }
    }
    
    private abstract class FireLinkEventListener extends FireSelectionListener {
        public void fire() { 
            _ui.debugMessage("fireLinkEvent for uri: " + _currentEventURI + " tag: " + _currentEventLinkTag);
            fireEvent(_currentEventLinkTag, _currentEventURI);
        }
        public abstract void fireEvent(HTMLTag tag, SyndieURI uri);
    }
    
    /**
     *  Menu shown when right clicking on anything that isn't a link or image.
     *
     *  _showForumMenu controls whether to show author/forum choices
     */
    private void buildBodyMenu() {
        _bodyMenu = new Menu(_text);
        _bodyMenu.setEnabled(true);
        _bodyMenu.addMenuListener(new MenuListener() {
            public void menuHidden(MenuEvent menuEvent) {}
            public void menuShown(MenuEvent menuEvent) {
                if (!_showForumMenu)
                    return;
                // if the user isn't authorized to post a reply to the forum, don't offer to let them
                long msgId = _msg != null ? _msg.getInternalId() : -1;
                if (_bodyReplyToForum.getEnabled() && MessagePreview.allowedToReply(_client, msgId))
                    _bodyReplyToForum.setEnabled(true);
                else
                    _bodyReplyToForum.setEnabled(false);
                boolean read = _client.getMessageStatus(msgId) == DBClient.MSG_STATUS_READ;
                _bodyMarkAsRead.setEnabled(!read);
                _bodyMarkAsUnread.setEnabled(read);
            }
        });

        if (_showForumMenu) {
            _bodyViewForum = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyViewForum.setText(getText("View forum"));
            _bodyViewForum.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.viewScopeMessages(PageRenderer.this, _msg.getTargetChannel()); 
                }
            });
            _bodyViewForumMetadata = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyViewForumMetadata.setText(getText("View forum profile"));
            _bodyViewForumMetadata.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.viewScopeMetadata(PageRenderer.this, _msg.getTargetChannel()); 
                }
            });
            new MenuItem(_bodyMenu, SWT.SEPARATOR);
            _bodyViewAuthorForum = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyViewAuthorForum.setText(getText("View author"));
            _bodyViewAuthorForum.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.viewScopeMessages(PageRenderer.this, _msg.getScopeChannel()); 
                }
            });
            _bodyViewAuthorMetadata = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyViewAuthorMetadata.setText(getText("View author profile"));
            _bodyViewAuthorMetadata.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.viewScopeMetadata(PageRenderer.this, _msg.getScopeChannel()); 
                }
            });
            new MenuItem(_bodyMenu, SWT.SEPARATOR);
            _bodyBookmarkForum = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyBookmarkForum.setImage(ImageUtil.ICON_ADDBOOKMARK);
            _bodyBookmarkForum.setText(getText("Bookmark forum"));
            _bodyBookmarkForum.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.bookmark(PageRenderer.this, SyndieURI.createScope(_msg.getTargetChannel()));
                }
            });
            _bodyBookmarkAuthor = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyBookmarkAuthor.setText(getText("Bookmark author"));
            _bodyBookmarkAuthor.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.bookmark(PageRenderer.this, SyndieURI.createScope(_msg.getScopeChannel()));
                }
            });

            new MenuItem(_bodyMenu, SWT.SEPARATOR);

            _bodyMarkAsRead = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyMarkAsRead.setText(getText("Mark as read"));
            _bodyMarkAsRead.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    _client.markMessageRead(_msg.getInternalId());
                    _dataCallback.readStatusUpdated();
                }
            });

            _bodyMarkAsUnread = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyMarkAsUnread.setText(getText("Mark as unread"));
            _bodyMarkAsUnread.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    _client.markMessageUnread(_msg.getInternalId());
                    _dataCallback.readStatusUpdated();
                }
            });

            new MenuItem(_bodyMenu, SWT.SEPARATOR);

            _bodyReplyToForum = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyReplyToForum.setText(getText("Public reply to forum"));
            _bodyReplyToForum.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.replyToForum(PageRenderer.this, _msg.getTargetChannel(), _msg.getURI());
                }
            });
            _bodyReplyToAuthor = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyReplyToAuthor.setText(getText("Private reply to author"));
            _bodyReplyToAuthor.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.privateReply(PageRenderer.this, getAuthorHash(), _msg.getURI());
                }
            });

            new MenuItem(_bodyMenu, SWT.SEPARATOR);

            _bodySaveAll = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodySaveAll.setText(getText("Save all images"));
            _bodySaveAll.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    saveAllImages();
                }
            });
        }
        
        _bodyDisable = new MenuItem(_bodyMenu, SWT.PUSH);
        _bodyDisable.setText(getText("Disable images"));
        _bodyDisable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        _bodyEnable = new MenuItem(_bodyMenu, SWT.PUSH);
        _bodyEnable.setText(getText("Enable images"));
        _bodyEnable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        _bodyViewAsText = new MenuItem(_bodyMenu, SWT.RADIO);
        _bodyViewAsText.setText(getText("View as plain text"));
        _bodyViewAsText.addSelectionListener(new FireSelectionListener() {
            public void fire() { toggleViewAsText(); }
        });
        _bodyViewUnstyled = new MenuItem(_bodyMenu, SWT.RADIO);
        _bodyViewUnstyled.setText(getText("View as unstyled HTML"));
        _bodyViewUnstyled.addSelectionListener(new FireSelectionListener() {
            public void fire() { toggleViewUnstyled(); }
        });
        _bodyViewStyled = new MenuItem(_bodyMenu, SWT.RADIO);
        _bodyViewStyled.setText(getText("View as styled HTML"));
        _bodyViewStyled.addSelectionListener(new FireSelectionListener() {
            public void fire() { toggleViewStyled(); }
        });
        _bodyViewStyled.setSelection(true);
        
        if (_showForumMenu) {
            new MenuItem(_bodyMenu, SWT.SEPARATOR);

            _bodyDelete = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyDelete.setText(getText("Delete this message locally"));
            _bodyDelete.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    deleteMessage();
                }
            });

            _bodyCancel = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyCancel.setText(getText("Cancel the message (tell others to delete it)"));
            _bodyCancel.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    cancelMessage();
                }
            });

            new MenuItem(_bodyMenu, SWT.SEPARATOR);
            _bodyBanForum = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyBanForum.setText(getText("Ban forum"));
            _bodyBanForum.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.banScope(PageRenderer.this, _msg.getTargetChannel());
                }
            });
            _bodyBanAuthor = new MenuItem(_bodyMenu, SWT.PUSH);
            _bodyBanAuthor.setText(getText("Ban author"));
            _bodyBanAuthor.addSelectionListener(new FireSelectionListener() { 
                public void fire() { 
                    if ( (_listener != null) && (_msg != null) )
                        _listener.banScope(PageRenderer.this, _msg.getScopeChannel());
                }
            });
        }
    }
    
    /** menu shown when right clicking on a link */
    private void buildLinkMenu() {
        _linkMenu = new Menu(_text);
        _linkMenu.setEnabled(true);
        
        _linkView = new MenuItem(_linkMenu, SWT.PUSH);
        _linkView.setText(getText("View link"));
        _linkView.addSelectionListener(new FireLinkEventListener() { 
            public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                    _listener.view(PageRenderer.this, uri);
                }
            }
        });
        new MenuItem(_linkMenu, SWT.SEPARATOR);
        _linkBookmark = new MenuItem(_linkMenu, SWT.PUSH);
        _linkBookmark.setText(getText("Bookmark link"));
        _linkBookmark.addSelectionListener(new FireLinkEventListener() { 
            public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                    _listener.bookmark(PageRenderer.this, uri);
                }
            }
        });
        if (_showForumMenu) {
            new MenuItem(_linkMenu, SWT.SEPARATOR);
            _linkImportReadKey = new MenuItem(_linkMenu, SWT.PUSH);
            _linkImportReadKey.setText(getText("Import read key"));
            _linkImportReadKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importReadKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getReadKey());
                    }
                }
            });
            _linkImportPostKey = new MenuItem(_linkMenu, SWT.PUSH);
            _linkImportPostKey.setText(getText("Import post key"));
            _linkImportPostKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importPostKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getPostKey());
                    }
                }
            });
            _linkImportManageKey = new MenuItem(_linkMenu, SWT.PUSH);
            _linkImportManageKey.setText(getText("Import manage key"));
            _linkImportManageKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importManageKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getManageKey());
                    }
                }
            });
            _linkImportReplyKey = new MenuItem(_linkMenu, SWT.PUSH);
            _linkImportReplyKey.setText(getText("Import reply key"));
            _linkImportReplyKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importReplyKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getReplyKey());
                    }
                }
            });
            _linkImportArchiveKey = new MenuItem(_linkMenu, SWT.PUSH);
            _linkImportArchiveKey.setText(getText("Import archive key"));
            _linkImportArchiveKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importArchiveKey(PageRenderer.this, getAuthorHash(), uri, uri.getArchiveKey());
                    }
                }
            });
        }
    }

    private Hash getAuthorHash() {
        long authorId = _msg.getAuthorChannelId();
        if (authorId != _msg.getScopeChannelId()) {
            return _source.getChannelHash(authorId);
        } else {
            return _msg.getScopeChannel();
        }
    }
    
    private void saveImage() {
        // prompt the user for where the image should be saved
        String name = getSuggestedName(_currentEventImageTag);
        if (_listener != null)
            _listener.saveImage(PageRenderer.this, name, _currentEventImage);
    }
    private void saveAllImages() {
        // prompt the user for where the images should be saved
        Map images = new HashMap();
        for (int i = 0; i < _imageTags.size(); i++) {
            HTMLTag tag = (HTMLTag)_imageTags.get(i);
            String suggestedName = getSuggestedName(tag);
            if (images.containsKey(suggestedName)) {
                int j = 1;
                while (images.containsKey(suggestedName + "." + j))
                    j++;
                suggestedName = suggestedName + "." + j;
            }
            for (int j = 0; j < _imageIndexes.size(); j++) {
                Integer idx = (Integer)_imageIndexes.get(j);
                if (idx.intValue() == tag.startIndex) {
                    images.put(suggestedName, _images.get(j));
                    break;
                }
            }
        }
        if (_listener != null)
            _listener.saveAllImages(PageRenderer.this, images);
    }
    
    private void cancelMessage() {
        if ( (_listener != null) && (_msg != null) )
            _listener.cancelMessage(PageRenderer.this, _msg.getURI());
    }
    private void deleteMessage() {
        if ( (_listener != null) && (_msg != null) )
            _listener.deleteMessage(PageRenderer.this, _msg.getURI());
    }
    
    private String getSuggestedName(HTMLTag tag) {
        SyndieURI imgURI = HTMLStyleBuilder.getURI(tag.getAttribValue("src"), _msg);
        if (imgURI != null) {
            Long attachmentId = imgURI.getLong("attachment");
            if (attachmentId != null) {
                // the attachment may not be from this message...
                Hash scope = imgURI.getScope();
                long scopeId = -1;
                Long msgId = imgURI.getMessageId();
                if ( (scope == null) || (msgId == null) ) {
                    // ok, yes, its implicitly from this message
                    //scope = _msg.getScopeChannel();
                    scopeId = _msg.getScopeChannelId();
                    msgId = Long.valueOf(_msg.getMessageId());
                } else {
                    scopeId = _source.getChannelId(scope);
                }
        
                long internalMsgId = _source.getMessageId(scopeId, msgId.longValue());
                Properties props = _source.getMessageAttachmentConfig(internalMsgId, attachmentId.intValue());
                String name = props.getProperty(Constants.MSG_ATTACH_NAME);
                if (name != null)
                    return StringUtil.stripFilename(name, false);
                else
                    return "attachment" + attachmentId.intValue() + ".png";
            }
        }
        return tag.hashCode() + ".png";
    }
    
    private void rerender() {
        // reparse/render/layout the text area, since the image/ban/etc changed
        //System.out.println("rerender");
        renderPage(_source, _msg, _page);
    }
    
    /** menu shown when right clicking on an image*/
    private void buildImageMenu() {
        _imageMenu = new Menu(_text);
        _imageMenu.setEnabled(true);
        
        /*
        _imgView = new MenuItem(_imageMenu, SWT.PUSH);
        _imgView.setText("View image");
        _imgView.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                if ( (_listener != null) && (_msg != null) && (_currentEventImage != null) )
                    _listener.viewImage(PageRenderer.this, _currentEventImage);
            }
        });
        _imgSave = new MenuItem(_imageMenu, SWT.PUSH);
        _imgSave.setText("Save image");
        _imgSave.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                if (_currentEventImage != null)
                    saveImage();
            }
        });
        _imgSaveAll = new MenuItem(_imageMenu, SWT.PUSH);
        _imgSaveAll.setText("Save all images");
        _imgSaveAll.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                saveAllImages();
            }
        });
        new MenuItem(_imageMenu, SWT.SEPARATOR);
         */
        _imgDisable = new MenuItem(_imageMenu, SWT.PUSH);
        _imgDisable.setText(getText("Disable images"));
        _imgDisable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        _imgEnable = new MenuItem(_imageMenu, SWT.PUSH);
        _imgEnable.setText(getText("Enable images"));
        _imgEnable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        if (_showForumMenu) {
            new MenuItem(_imageMenu, SWT.SEPARATOR);
            _imgIgnoreForum= new MenuItem(_imageMenu, SWT.PUSH);
            _imgIgnoreForum.setText(getText("Ignore images in this forum"));
            _imgIgnoreForum.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.ignoreImageScope(PageRenderer.this, _msg.getTargetChannel());
                    rerender();
                }
            });
            _imgIgnoreAuthor= new MenuItem(_imageMenu, SWT.PUSH);
            _imgIgnoreAuthor.setText(getText("Ignore images from this author"));
            _imgIgnoreAuthor.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.ignoreImageScope(PageRenderer.this, _msg.getScopeChannel());
                    rerender();
                }
            });
        }
    }
    
    /** menu shown when right clicking on an image that is inside a hyperlink */
    private void buildImageLinkMenu() {
        _imageLinkMenu = new Menu(_text);
        _imageLinkMenu.setEnabled(true);
        
        _imgLinkViewLink = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkViewLink.setText(getText("View link"));
        _imgLinkViewLink.addSelectionListener(new FireLinkEventListener() { 
            public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                    _listener.view(PageRenderer.this, uri);
                }
            }
        });
        _imgLinkViewImg = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkViewImg.setText(getText("View image"));
        _imgLinkViewImg.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                if ( (_listener != null) && (_msg != null) && (_currentEventImage != null) )
                    _listener.viewImage(PageRenderer.this, _currentEventImage);
            }
        });
        _imgLinkBookmarkLink = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkBookmarkLink.setText(getText("Bookmark link"));
        _imgLinkBookmarkLink.addSelectionListener(new FireLinkEventListener() { 
            public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                    _listener.bookmark(PageRenderer.this, uri);
                }
            }
        });
        new MenuItem(_imageLinkMenu, SWT.SEPARATOR);
        
        _imgLinkSave = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkSave.setText(getText("Save image"));
        _imgLinkSave.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                if (_currentEventImage != null)
                    saveImage();
            }
        });
        _imgLinkSaveAll = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkSaveAll.setText(getText("Save all images"));
        _imgLinkSaveAll.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                saveAllImages();
            }
        });
        _imgLinkDisable = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkDisable.setText(getText("Disable images"));
        _imgLinkDisable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        _imgLinkEnable = new MenuItem(_imageLinkMenu, SWT.PUSH);
        _imgLinkEnable.setText(getText("Enable images"));
        _imgLinkEnable.addSelectionListener(new FireSelectionListener() {
            public void fire() {
                toggleImages();
            }
        });
        new MenuItem(_imageLinkMenu, SWT.SEPARATOR);
        
        if (_showForumMenu) {
            _imgLinkIgnoreForum = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkIgnoreForum.setText(getText("Ignore images in this forum"));
            _imgLinkIgnoreForum.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.ignoreImageScope(PageRenderer.this, _msg.getTargetChannel());
                    rerender();
                }
            });
            _imgLinkIgnoreAuthor = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkIgnoreAuthor.setText(getText("Ignore images from this author"));
            _imgLinkIgnoreAuthor.addSelectionListener(new FireSelectionListener() {
                public void fire() {
                    if ( (_listener != null) && (_msg != null) )
                        _listener.ignoreImageScope(PageRenderer.this, _msg.getScopeChannel());
                    rerender();
                }
            });
            new MenuItem(_imageLinkMenu, SWT.SEPARATOR);

            _imgLinkImportReadKey = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkImportReadKey.setText(getText("Import read key"));
            _imgLinkImportReadKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importReadKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getReadKey());
                    }
                }
            });
            _imgLinkImportPostKey = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkImportPostKey.setText(getText("Import post key"));
            _imgLinkImportPostKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importPostKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getPostKey());
                    }
                }
            });
            _imgLinkImportManageKey = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkImportManageKey.setText(getText("Import manage key"));
            _imgLinkImportManageKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importManageKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getManageKey());
                    }
                }
            });
            _imgLinkImportReplyKey = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkImportReplyKey.setText(getText("Import reply key"));
            _imgLinkImportReplyKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importReplyKey(PageRenderer.this, getAuthorHash(), uri.getScope(), uri.getReplyKey());
                    }
                }
            });
            _imgLinkImportArchiveKey = new MenuItem(_imageLinkMenu, SWT.PUSH);
            _imgLinkImportArchiveKey.setText(getText("Import archive key"));
            _imgLinkImportArchiveKey.addSelectionListener(new FireLinkEventListener() { 
                public void fireEvent(HTMLTag tag, SyndieURI uri) { 
                    if ( (_listener != null) && (_msg != null) && (uri != null) ) {
                        _listener.importArchiveKey(PageRenderer.this, getAuthorHash(), uri, uri.getArchiveKey());
                    }
                }
            });
        }
    }
    
    public interface PageActionListener {
        /**
         * The user wants to view the list of messages in the given scope (forum/blog)
         */
        public void viewScopeMessages(PageRenderer renderer, Hash scope);
        /**
         * The user wants to view the description for the given scope (forum/blog)
         */
        public void viewScopeMetadata(PageRenderer renderer, Hash scope);
        /**
         * The user wants to view the given uri (may refer to a syndie location, archive, external url, etc) 
         */
        public void view(PageRenderer renderer, SyndieURI uri);
        /**
         * The user wants to bookmark the given uri (perhaps prompt them where they want to bookmark it, and what they want to call it?)
         */
        public void bookmark(PageRenderer renderer, SyndieURI uri);
        /**
         * The user never wants to see the given scope again
         */
        public void banScope(PageRenderer renderer, Hash scope);
        /**
         * Display the image
         */
        public void viewImage(PageRenderer renderer, Image img);
        /**
         * The user never wants to see images from the given author (or in the given forum)
         */
        public void ignoreImageScope(PageRenderer renderer, Hash scope);
        
        /**
         * Import the read key for posts on the given scope
         * @param referencedBy who gave us the key
         * @param keyScope what forum/blog the key is valid for
         */
        public void importReadKey(PageRenderer renderer, Hash referencedBy, Hash keyScope, SessionKey key);
        /**
         * Import the post key to post on the given scope
         * @param referencedBy who gave us the key
         * @param keyScope what forum/blog the key is valid for
         */
        public void importPostKey(PageRenderer renderer, Hash referencedBy, Hash keyScope, SigningPrivateKey key);
        /**
         * Import the manage key to manage the given scope
         * @param referencedBy who gave us the key
         * @param keyScope what forum/blog the key is valid for
         */
        public void importManageKey(PageRenderer renderer, Hash referencedBy, Hash keyScope, SigningPrivateKey key);
        /**
         * Import the reply key to decrypt posts in the given scope
         * @param referencedBy who gave us the key
         * @param keyScope what forum/blog the key is valid for
         */
        public void importReplyKey(PageRenderer renderer, Hash referencedBy, Hash keyScope, PrivateKey key);
        /**
         * Import the archive key to contact the given archive
         * @param referencedBy who gave us the key
         * @param archiveURI what archive the key is valid for
         */
        public void importArchiveKey(PageRenderer renderer, Hash referencedBy, SyndieURI archiveURI, SessionKey key);
        
        /**
         * The user wants to save the given images
         * @param images map of suggested filename (without any path) to the actual swt Image
         */
        public void saveAllImages(PageRenderer renderer, Map images);
        /**
         * The user wants to save the given image
         */
        public void saveImage(PageRenderer renderer, String suggestedName, Image img);
        /** 
         * the message should be deleted locally 
         */
        public void deleteMessage(PageRenderer renderer, SyndieURI msg);
        /** 
         * the message should be cancelled
         */
        public void cancelMessage(PageRenderer renderer, SyndieURI msg);
        /**
         * The user wants to create a reply that is readable only by the target author
         */
        public void privateReply(PageRenderer renderer, Hash author, SyndieURI msg);

        /**
         * The user wants to post up a reply to the given forum
         */
        public void replyToForum(PageRenderer renderer, Hash forum, SyndieURI msg);
        
        public void nextPage();
        public void prevPage();
    }
    
    public void applyTheme(Theme theme) {
        if (_msg == null) return;
        // old fonts are disposed and new ones created in the HTMLStyleBuilder
        long before = System.currentTimeMillis();
        rerender();
        long after = System.currentTimeMillis();
        _ui.debugMessage("applyTheme to pageRenderer: render took " + (after-before));
    }
    
/****
    public static void main(String args[]) {
        //String content = "<html><b>hi</b><br />foo</html>";
        String content = "normal<quote>first level<quote>second level<quote>third level</quote>second level</quote>first level</quote>normal";

        //try {
        //    FileInputStream fis = new FileInputStream("/tmp/syndie-log-1.txt");
        //    StringBuilder buf = new StringBuilder();
        //    String line = null;
        //    BufferedReader in = new BufferedReader(new InputStreamReader(fis));
        //    while ( (line = in.readLine()) != null)
        //        buf.append(line).append("\n");
        //    fis.close();
        //    content = buf.toString();
        //} catch (IOException ioe) {}

        
        UI ui = new NullUI() { 
            public void debugMessage(String msg) { debugMessage(msg, null); }
            public void debugMessage(String msg, Exception e) { 
                System.out.println(msg); 
                if (e != null)
                    e.printStackTrace();
            } 
        };
        Display d = Display.getDefault();
        final Shell shell = new Shell(d, SWT.SHELL_TRIM);
        shell.setLayout(new FillLayout());
        DummyBrowserControl control = new DummyBrowserControl(null, ui);
        PageRenderer renderer = new PageRenderer(control.getClient(), control.getUI(), control.getThemeRegistry(), control.getTranslationRegistry(), shell, control);
        ArrayList pages = new ArrayList();
        ArrayList attachments = new ArrayList();
        ArrayList attachmentOrder = new ArrayList();
        pages.add(content);

        MessageInfo msgInfo = new MessageInfo();
        msgInfo.setURI(PageRendererTab.DUMMY_URI);
        msgInfo.setTargetChannel(PageRendererTab.DUMMY_URI.getScope());
        msgInfo.setTargetChannelId(Long.MAX_VALUE);
        msgInfo.setScopeChannelId(Long.MAX_VALUE);
        msgInfo.setAuthorChannelId(Long.MAX_VALUE);
        msgInfo.setInternalId(Long.MAX_VALUE);
        msgInfo.setMessageId(PageRendererTab.DUMMY_URI.getMessageId().longValue());
        msgInfo.setPageCount(1);
        
        renderer.renderPage(new PageRendererSourceMem(control.getClient(), control.getThemeRegistry(), msgInfo, pages, attachments, attachmentOrder, null), PageRendererTab.DUMMY_URI);
        shell.setMaximized(true);
        shell.open();
        shell.addShellListener(new ShellListener() {
            public void shellActivated(ShellEvent shellEvent) {}
            public void shellClosed(ShellEvent shellEvent) { shell.dispose(); }
            public void shellDeactivated(ShellEvent shellEvent) {}
            public void shellDeiconified(ShellEvent shellEvent) {}
            public void shellIconified(ShellEvent shellEvent) {}
        });

        while (!shell.isDisposed()) {
            try { 
                if (!d.readAndDispatch()) d.sleep(); 
            } catch (RuntimeException e) {
                ui.debugMessage("Internal error", e);
            }
        }
    }
****/
}
