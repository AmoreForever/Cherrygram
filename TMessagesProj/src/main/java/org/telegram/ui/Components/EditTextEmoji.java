package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.PremiumPreviewFragment;

public class EditTextEmoji extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate {

    private EditTextCaption editText;
    private ImageView emojiButton;
    private ReplaceableIconDrawable emojiIconDrawable;
    private EmojiView emojiView;
    private boolean emojiViewVisible;
    private SizeNotifierFrameLayout sizeNotifierLayout;
    private BaseFragment parentFragment;

    private ItemOptions formatOptions;
    private boolean shownFormatButton;

    private int keyboardHeight;
    private int keyboardHeightLand;
    private boolean keyboardVisible;
    private int emojiPadding;
    private boolean destroyed;
    private boolean isPaused = true;
    private boolean showKeyboardOnResume;
    private int lastSizeChangeValue1;
    private boolean lastSizeChangeValue2;
    private int innerTextChange;
    private boolean allowAnimatedEmoji;
    public boolean includeNavigationBar;
    AdjustPanLayoutHelper adjustPanLayoutHelper;

    private EditTextEmojiDelegate delegate;

    private int currentStyle;
    private final Theme.ResourcesProvider resourcesProvider;

    public static final int STYLE_FRAGMENT = 0;
    public static final int STYLE_DIALOG = 1;
    public static final int STYLE_STORY = 2;
    public static final int STYLE_PHOTOVIEWER = 3;

    private boolean waitingForKeyboardOpen;
    private boolean isAnimatePopupClosing;
    private Runnable openKeyboardRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed && editText != null && waitingForKeyboardOpen && !keyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow && AndroidUtilities.isTablet()) {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    };

    public boolean isPopupVisible() {
        return emojiView != null && emojiView.getVisibility() == View.VISIBLE;
    }

    public boolean isWaitingForKeyboardOpen() {
        return waitingForKeyboardOpen;
    }

    public boolean isAnimatePopupClosing() {
        return isAnimatePopupClosing;
    }

    public void setAdjustPanLayoutHelper(AdjustPanLayoutHelper adjustPanLayoutHelper) {
        this.adjustPanLayoutHelper = adjustPanLayoutHelper;
    }

    public interface EditTextEmojiDelegate {
        void onWindowSizeChanged(int size);
    }

    public EditTextEmoji(Context context, SizeNotifierFrameLayout parent, BaseFragment fragment, int style, boolean allowAnimatedEmoji) {
        this(context, parent, fragment, style, allowAnimatedEmoji, null);
    }
    
    public EditTextEmoji(Context context, SizeNotifierFrameLayout parent, BaseFragment fragment, int style, boolean allowAnimatedEmoji, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.allowAnimatedEmoji = allowAnimatedEmoji;
        this.resourcesProvider = resourcesProvider;
        currentStyle = style;

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        parentFragment = fragment;
        sizeNotifierLayout = parent;
        sizeNotifierLayout.setDelegate(this);

        editText = new EditTextCaption(context, resourcesProvider) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (isPopupShowing() && event.getAction() == MotionEvent.ACTION_DOWN) {
                    onWaitingForKeyboard();
                    showPopup(AndroidUtilities.usingHardwareInput ? 0 : 2);
                    openKeyboardInternal();
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    requestFocus();
                    if (!AndroidUtilities.showKeyboard(this)) {
                        clearFocus();
                        requestFocus();
                    }
                }
                try {
                    return super.onTouchEvent(event);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                return false;
            }

            @Override
            protected void onLineCountChanged(int oldLineCount, int newLineCount) {
                EditTextEmoji.this.onLineCountChanged(oldLineCount, newLineCount);
            }

            @Override
            protected int getActionModeStyle() {
                if (style == STYLE_STORY || style == STYLE_PHOTOVIEWER) {
                    return FloatingToolbar.STYLE_BLACK;
                }
                return super.getActionModeStyle();
            }

            @Override
            protected void extendActionMode(ActionMode actionMode, Menu menu) {
                if (allowEntities()) {
                    ChatActivity.fillActionModeMenu(menu, null, currentStyle == STYLE_PHOTOVIEWER);
                }
                super.extendActionMode(actionMode, menu);
            }

            @Override
            public void scrollTo(int x, int y) {
                if (EditTextEmoji.this.onScrollYChange(y)) {
                    super.scrollTo(x, y);
                }
            }

            private Drawable lastIcon = null;

            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);

                if (emojiIconDrawable != null) {
                    boolean selected = selEnd != selStart;
                    boolean showFormat = allowEntities() && selected && (XiaomiUtilities.isMIUI() || true);
                    if (shownFormatButton != showFormat) {
                        shownFormatButton = showFormat;
                        if (showFormat) {
                            lastIcon = emojiIconDrawable.getIcon();
                            emojiIconDrawable.setIcon(R.drawable.msg_edit, true);
                        } else {
                            emojiIconDrawable.setIcon(lastIcon, true);
                            lastIcon = null;
                        }
                    }
                }
            }
        };
        editText.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setInputType(editText.getInputType() | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setFocusable(editText.isEnabled());
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        if (style == STYLE_FRAGMENT) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setMaxLines(4);
            editText.setGravity(Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));
            editText.setBackground(null);
            editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
            editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
            editText.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), AndroidUtilities.dp(8));
            addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, LocaleController.isRTL ? 11 : 0, 1, LocaleController.isRTL ? 0 : 11, 0));
        } else if (style == STYLE_STORY || style == STYLE_PHOTOVIEWER) {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setMaxLines(8);
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            editText.setAllowTextEntitiesIntersection(true);
            editText.setHintTextColor(0x8cffffff);
            editText.setTextColor(0xffffffff);
            editText.setCursorColor(0xffffffff);
            editText.setBackground(null);
            editText.setClipToPadding(false);
            editText.setPadding(0, AndroidUtilities.dp(9), 0, AndroidUtilities.dp(9));
            editText.setHandlesColor(0xffffffff);
            editText.setHighlightColor(0x30ffffff);
            editText.setLinkTextColor(0xFF46A3EB);
            editText.quoteColor = 0xffffffff;
            editText.setTextIsSelectable(true);
            setClipChildren(false);
            setClipToPadding(false);
            addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 24, 0));
        } else {
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            editText.setMaxLines(4);
            editText.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
            editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
            editText.setBackground(null);
            editText.setPadding(0, AndroidUtilities.dp(11), 0, AndroidUtilities.dp(12));
            addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 48, 0, 0, 0));
        }

        emojiButton = new ImageView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!customEmojiButtonDraw(canvas, emojiButton, emojiIconDrawable)) {
                    super.dispatchDraw(canvas);
                }
            }
        };
        emojiButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        emojiButton.setImageDrawable(emojiIconDrawable = new ReplaceableIconDrawable(context));
        if (style == STYLE_FRAGMENT) {
            emojiIconDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            emojiIconDrawable.setIcon(R.drawable.smiles_tab_smiles, false);
            addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 0, 0, 0, 7));
        } else if (style == STYLE_STORY || style == STYLE_PHOTOVIEWER) {
            emojiIconDrawable.setColorFilter(new PorterDuffColorFilter(0x8cffffff, PorterDuff.Mode.MULTIPLY));
            emojiIconDrawable.setIcon(R.drawable.input_smile, false);
            addView(emojiButton, LayoutHelper.createFrame(40, 40, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
        } else {
            emojiIconDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
            emojiIconDrawable.setIcon(R.drawable.input_smile, false);
            addView(emojiButton, LayoutHelper.createFrame(48, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
        }
        if (Build.VERSION.SDK_INT >= 21) {
            emojiButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        }
        emojiButton.setOnClickListener(view -> {
            if (!emojiButton.isEnabled() || emojiButton.getAlpha() < 0.5f || (adjustPanLayoutHelper != null && adjustPanLayoutHelper.animationInProgress())) {
                return;
            }
            if (shownFormatButton) {
                if (formatOptions == null) {
                    editText.hideActionMode();
                    ItemOptions itemOptions = ItemOptions.makeOptions(parent, resourcesProvider, emojiButton);
                    itemOptions.setMaxHeight(AndroidUtilities.dp(280));
                    editText.extendActionMode(null, new MenuToItemOptions(itemOptions, editText::performMenuAction, editText.getOnPremiumMenuLockClickListener()));
                    itemOptions.forceTop(true);
                    itemOptions.show();
                } else {
                    formatOptions.dismiss();
                    formatOptions = null;
                }
            } else if (!isPopupShowing()) {
                showPopup(1);
                emojiView.onOpen(editText.length() > 0);
                editText.requestFocus();
            } else {
                openKeyboardInternal();
            }
        });
        emojiButton.setContentDescription(LocaleController.getString("Emoji", R.string.Emoji));
    }

    protected boolean allowEntities() {
        return currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER;
    }

    public void setSuggestionsEnabled(boolean enabled) {
        int inputType = editText.getInputType();
        if (!enabled) {
            inputType |= EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        } else {
            inputType &= ~EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        if (editText.getInputType() != inputType) {
            editText.setInputType(inputType);
        }
    }

    protected boolean onScrollYChange(int scrollY) {
        return true;
    }

    protected boolean customEmojiButtonDraw(Canvas canvas, View button, Drawable drawable) {
        return false;
    }

    protected void onLineCountChanged(int oldLineCount, int newLineCount) {

    }

    public void setSizeNotifierLayout(SizeNotifierFrameLayout layout) {
        sizeNotifierLayout = layout;
        sizeNotifierLayout.setDelegate(this);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (emojiView != null) {
                emojiView.invalidateViews();
            }
            if (editText != null) {
                int color = editText.getCurrentTextColor();
                editText.setTextColor(0xffffffff);
                editText.setTextColor(color);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        editText.setEnabled(enabled);
        emojiButton.setVisibility(enabled ? VISIBLE : GONE);
        if (enabled) {
            editText.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0, LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), AndroidUtilities.dp(8));
        } else {
            editText.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        }
    }

    @Override
    public void setFocusable(boolean focusable) {
        editText.setFocusable(focusable);
    }

    public void hideEmojiView() {
        if (!emojiViewVisible && emojiView != null && emojiView.getVisibility() != GONE) {
            emojiView.setVisibility(GONE);
        }
        emojiPadding = 0;
    }

    public EmojiView getEmojiView() {
        return emojiView;
    }

    public void setDelegate(EditTextEmojiDelegate editTextEmojiDelegate) {
        delegate = editTextEmojiDelegate;
    }

    public void onPause() {
        isPaused = true;
        closeKeyboard();
    }

    public void onResume() {
        isPaused = false;
        if (showKeyboardOnResume) {
            showKeyboardOnResume = false;
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
            if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
                waitingForKeyboardOpen = true;
                onWaitingForKeyboard();
                AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
                AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
            }
        }
    }

    public void onDestroy() {
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (emojiView != null) {
            emojiView.onDestroy();
        }
        if (sizeNotifierLayout != null) {
            sizeNotifierLayout.setDelegate(null);
        }
    }

    public void updateColors() {
        if (currentStyle == STYLE_FRAGMENT) {
            editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));
            editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        } else if (currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER) {
            editText.setHintTextColor(0x8cffffff);
            editText.setTextColor(0xffffffff);
            editText.setCursorColor(0xffffffff);
            editText.setHandlesColor(0xffffffff);
            editText.setHighlightColor(0x30ffffff);
            editText.quoteColor = 0xffffffff;
        } else {
            editText.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
            editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        }
        emojiIconDrawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.MULTIPLY));
        if (emojiView != null) {
            emojiView.updateColors();
        }
    }

    public void setMaxLines(int value) {
        editText.setMaxLines(value);
    }

    public int length() {
        return editText.length();
    }

    public void setFilters(InputFilter[] filters) {
        editText.setFilters(filters);
    }

    public Editable getText() {
        return editText.getText();
    }

    public void setHint(CharSequence hint) {
        editText.setHint(hint);
    }

    public void setText(CharSequence text) {
        editText.setText(text);
    }

    public void setSelection(int selection) {
        editText.setSelection(selection);
    }

    public void hidePopup(boolean byBackButton) {
        if (isPopupShowing()) {
            showPopup(0);
        }
        if (byBackButton) {
            if (emojiView != null && emojiView.getVisibility() == View.VISIBLE && !waitingForKeyboardOpen) {
                int height = emojiView.getMeasuredHeight();
                if (emojiView.getParent() instanceof ViewGroup) {
                    height += ((ViewGroup) emojiView.getParent()).getHeight() - emojiView.getBottom();
                }
                final int finalHeight = height;
                ValueAnimator animator = ValueAnimator.ofFloat(0, finalHeight);
                animator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    emojiView.setTranslationY(v);
                    if (finalHeight > 0 && (currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER)) {
                        emojiView.setAlpha(1f - v / (float) finalHeight);
                    }
                    bottomPanelTranslationY(v - finalHeight);
                });
                isAnimatePopupClosing = true;
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        isAnimatePopupClosing = false;
                        emojiView.setTranslationY(0);
                        emojiView.setAlpha(0);
                        bottomPanelTranslationY(0);
                        hideEmojiView();
                    }
                });
                animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                animator.start();
            } else {
                hideEmojiView();
            }
        }
    }

    protected void bottomPanelTranslationY(float translation) {

    }

    public void openKeyboard() {
        AndroidUtilities.showKeyboard(editText);
    }

    public void closeKeyboard() {
        AndroidUtilities.hideKeyboard(editText);
    }

    public boolean isPopupShowing() {
        return emojiViewVisible;
    }

    public boolean isKeyboardVisible() {
        return keyboardVisible;
    }

    protected void openKeyboardInternal() {
        onWaitingForKeyboard();
        showPopup(AndroidUtilities.usingHardwareInput || isPaused ? 0 : 2);
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
        if (isPaused) {
            showKeyboardOnResume = true;
        } else if (!AndroidUtilities.usingHardwareInput && !keyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
            waitingForKeyboardOpen = true;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
            AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100);
        }
    }

    protected void showPopup(int show) {
        if (show == 1) {
            boolean emojiWasVisible = emojiView != null && emojiView.getVisibility() == View.VISIBLE;
            createEmojiView();

            emojiView.setVisibility(VISIBLE);
            emojiViewVisible = true;
            View currentView = emojiView;

            if (keyboardHeight <= 0) {
                if (AndroidUtilities.isTablet()) {
                    keyboardHeight = AndroidUtilities.dp(150);
                } else {
                    keyboardHeight = MessagesController.getGlobalEmojiSettings().getInt("kbd_height", AndroidUtilities.dp(200));
                }
            }
            if (keyboardHeightLand <= 0) {
                if (AndroidUtilities.isTablet()) {
                    keyboardHeightLand = AndroidUtilities.dp(150);
                } else {
                    keyboardHeightLand = MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", AndroidUtilities.dp(200));
                }
            }
            int currentHeight = (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight)  + (includeNavigationBar ? AndroidUtilities.navigationBarHeight : 0);

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) currentView.getLayoutParams();
            layoutParams.height = currentHeight;
            currentView.setLayoutParams(layoutParams);
            if (!AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
                AndroidUtilities.hideKeyboard(editText);
            }
            if (sizeNotifierLayout != null) {
                emojiPadding = currentHeight;
                sizeNotifierLayout.requestLayout();
                emojiIconDrawable.setIcon(R.drawable.input_keyboard, true);
                onWindowSizeChanged();
            }
            onEmojiKeyboardUpdate();

            if (!keyboardVisible && !emojiWasVisible) {
                ValueAnimator animator = ValueAnimator.ofFloat(emojiPadding, 0);
                animator.addUpdateListener(animation -> {
                    float v = (float) animation.getAnimatedValue();
                    emojiView.setTranslationY(v);
                    if (emojiPadding > 0 && (currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER)) {
                        emojiView.setAlpha(1f - v / (float) emojiPadding);
                    }
                    bottomPanelTranslationY(v);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        emojiView.setTranslationY(0);
                        emojiView.setAlpha(1f);
                        bottomPanelTranslationY(0);
                    }
                });
                animator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                animator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                animator.start();
            } else {
                emojiView.setAlpha(1f);
            }
        } else {
            if (emojiButton != null) {
                if (currentStyle == STYLE_FRAGMENT) {
                    emojiIconDrawable.setIcon(R.drawable.smiles_tab_smiles, true);
                } else {
                    emojiIconDrawable.setIcon(R.drawable.input_smile, true);
                }
            }
            if (emojiView != null) {
                emojiViewVisible = false;
                onEmojiKeyboardUpdate();
                if (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
                    emojiView.setVisibility(GONE);
                }
            }
            if (sizeNotifierLayout != null) {
                if (show == 0) {
                    emojiPadding = 0;
                }
                sizeNotifierLayout.requestLayout();
                onWindowSizeChanged();
            }
        }
    }

    private void onWindowSizeChanged() {
        int size = sizeNotifierLayout.getHeight();
        if (!keyboardVisible) {
            size -= emojiPadding;
        }
        if (delegate != null) {
            delegate.onWindowSizeChanged(size);
        }
    }

    protected void closeParent() {

    }

    protected void drawEmojiBackground(Canvas canvas, View view) {

    }

    protected void createEmojiView() {
        if (emojiView != null && emojiView.currentAccount != UserConfig.selectedAccount) {
            sizeNotifierLayout.removeView(emojiView);
            emojiView = null;
        }
        if (emojiView != null) {
            return;
        }
        emojiView = new EmojiView(parentFragment, allowAnimatedEmoji, false, false, getContext(), false, null, null, currentStyle != STYLE_STORY && currentStyle != STYLE_PHOTOVIEWER, resourcesProvider, false) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER) {
                    drawEmojiBackground(canvas, this);
                }
                super.dispatchDraw(canvas);
            }
        };
        emojiView.setVisibility(GONE);
        if (AndroidUtilities.isTablet()) {
            emojiView.setForseMultiwindowLayout(true);
        }
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public boolean onBackspace() {
                if (editText.length() == 0) {
                    return false;
                }
                editText.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                return true;
            }

            @Override
            public void onAnimatedEmojiUnlockClick() {
                BaseFragment fragment = parentFragment;
                if (fragment == null) {
                    fragment = new BaseFragment() {
                        @Override
                        public int getCurrentAccount() {
                            return currentAccount;
                        }

                        @Override
                        public Context getContext() {
                            return EditTextEmoji.this.getContext();
                        }

                        @Override
                        public Activity getParentActivity() {
                            Context context = getContext();
                            while (context instanceof ContextWrapper) {
                                if (context instanceof Activity) {
                                    return (Activity) context;
                                }
                                context = ((ContextWrapper) context).getBaseContext();
                            }
                            return null;
                        }

                        @Override
                        public Dialog getVisibleDialog() {
                            return new Dialog(EditTextEmoji.this.getContext()) {
                                @Override
                                public void dismiss() {
                                    hidePopup(false);
                                    closeParent();
                                }
                            };
                        }
                    };
                    new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
                } else {
                    fragment.showDialog(new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false));
                }
            }

            @Override
            public void onEmojiSelected(String symbol) {
                int i = editText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = 2;
                    CharSequence localCharSequence = Emoji.replaceEmoji(symbol, editText.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
                    editText.setText(editText.getText().insert(i, localCharSequence));
                    int j = i + localCharSequence.length();
                    editText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = 0;
                }
            }

            @Override
            public void onCustomEmojiSelected(long documentId, TLRPC.Document document,  String emoticon, boolean isRecent) {
                int i = editText.getSelectionEnd();
                if (i < 0) {
                    i = 0;
                }
                try {
                    innerTextChange = 2;
                    SpannableString spannable = new SpannableString(emoticon);
                    AnimatedEmojiSpan span;
                    if (document != null) {
                        span = new AnimatedEmojiSpan(document, editText.getPaint().getFontMetricsInt());
                    } else {
                        span = new AnimatedEmojiSpan(documentId, editText.getPaint().getFontMetricsInt());
                    }
                    span.cacheType = emojiView.emojiCacheType;
                    spannable.setSpan(span, 0, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    editText.setText(editText.getText().insert(i, spannable));
                    int j = i + spannable.length();
                    editText.setSelection(j, j);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    innerTextChange = 0;
                }
            }

            @Override
            public void onClearEmojiRecent() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), resourcesProvider);
                builder.setTitle(LocaleController.getString("ClearRecentEmojiTitle", R.string.ClearRecentEmojiTitle));
                builder.setMessage(LocaleController.getString("ClearRecentEmojiText", R.string.ClearRecentEmojiText));
                builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton), (dialogInterface, i) -> emojiView.clearRecentEmoji());
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                if (parentFragment != null) {
                    parentFragment.showDialog(builder.create());
                } else {
                    builder.show();
                }
            }
        });
        sizeNotifierLayout.addView(emojiView);
    }

    protected void onEmojiKeyboardUpdate() {}
    protected void onWaitingForKeyboard() {}

    public boolean isPopupView(View view) {
        return view == emojiView;
    }

    public int getEmojiPadding() {
        return emojiPadding;
    }

    public int getKeyboardHeight() {
        return (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? keyboardHeightLand : keyboardHeight) + (includeNavigationBar ? AndroidUtilities.navigationBarHeight : 0);
    }

    @Override
    public void onSizeChanged(int height, boolean isWidthGreater) {
        if (height > AndroidUtilities.dp(50) && (keyboardVisible || currentStyle == STYLE_STORY || currentStyle == STYLE_PHOTOVIEWER) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
            if (isWidthGreater) {
                keyboardHeightLand = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).apply();
            } else {
                keyboardHeight = height;
                MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).apply();
            }
        }

        if (isPopupShowing()) {
            int newHeight = (isWidthGreater ? keyboardHeightLand : keyboardHeight) + (includeNavigationBar ? AndroidUtilities.navigationBarHeight : 0);;

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
            if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
                layoutParams.width = AndroidUtilities.displaySize.x;
                layoutParams.height = newHeight;
                emojiView.setLayoutParams(layoutParams);
                if (sizeNotifierLayout != null) {
                    emojiPadding = layoutParams.height;
                    sizeNotifierLayout.requestLayout();
                    onWindowSizeChanged();
                }
            }
        }

        if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
            onWindowSizeChanged();
            return;
        }
        lastSizeChangeValue1 = height;
        lastSizeChangeValue2 = isWidthGreater;

        boolean oldValue = keyboardVisible;
        keyboardVisible = editText.isFocused() && height > 0;
        if (keyboardVisible && isPopupShowing()) {
            showPopup(0);
        }
        if (emojiPadding != 0 && !keyboardVisible && keyboardVisible != oldValue && !isPopupShowing()) {
            emojiPadding = 0;
            sizeNotifierLayout.requestLayout();
        }
        if (keyboardVisible && waitingForKeyboardOpen) {
            waitingForKeyboardOpen = false;
            AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable);
        }
        onWindowSizeChanged();
    }

    public EditTextCaption getEditText() {
        return editText;
    }

    public View getEmojiButton() {
        return emojiButton;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
