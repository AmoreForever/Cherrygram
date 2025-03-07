package org.telegram.ui.Components.Premium.boosts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_CHANNEL;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_COUNTRY;
import static org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet.TYPE_USER;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberPicker;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.StatisticActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class BoostDialogs {
    private final static long ONE_DAY = 1000 * 60 * 60 * 24;

    public static long getThreeDaysAfterToday() {
        return BoostDialogs.roundByFiveMinutes(new Date().getTime() + (ONE_DAY * 3));
    }

    public static void showToastError(Context context, TLRPC.TL_error error) {
        if (error != null && error.text != null && !TextUtils.isEmpty(error.text)) {
            Toast.makeText(context, error.text, Toast.LENGTH_LONG).show();
        }
    }

    private static void showBulletin(final BulletinFactory bulletinFactory, Theme.ResourcesProvider resourcesProvider, final TLRPC.Chat chat, final boolean isGiveaway) {
        AndroidUtilities.runOnUIThread(() -> bulletinFactory.createSimpleBulletin(R.raw.star_premium_2,
                isGiveaway ? getString("BoostingGiveawayCreated", R.string.BoostingGiveawayCreated)
                        : getString("BoostingAwardsCreated", R.string.BoostingAwardsCreated),
                AndroidUtilities.replaceSingleTag(
                        isGiveaway ? getString("BoostingCheckStatistic", R.string.BoostingCheckStatistic) :
                                getString("BoostingCheckGiftsStatistic", R.string.BoostingCheckGiftsStatistic),
                        Theme.key_undo_cancelColor, 0, () -> {
                            if (chat != null) {
                                Bundle args = new Bundle();
                                args.putLong("chat_id", chat.id);
                                args.putBoolean("is_megagroup", chat.megagroup);
                                args.putBoolean("start_from_boosts", true);
                                args.putBoolean("only_boosts", true);
                                StatisticActivity fragment = new StatisticActivity(args);
                                BaseFragment.BottomSheetParams params = new BaseFragment.BottomSheetParams();
                                params.transitionFromLeft = true;
                                LaunchActivity.getLastFragment().showAsSheet(fragment, params);
                            }
                        }, resourcesProvider)
        ).setDuration(Bulletin.DURATION_PROLONG).show(), 300);
    }

    public static void showGiftLinkForwardedBulletin(long did) {
        CharSequence text;
        if (did == UserConfig.getInstance(UserConfig.selectedAccount).clientUserId) {
            text = AndroidUtilities.replaceTags(LocaleController.getString("BoostingGiftLinkForwardedToSavedMsg", R.string.BoostingGiftLinkForwardedToSavedMsg));
        } else {
            if (DialogObject.isChatDialog(did)) {
                TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-did);
                text = AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGiftLinkForwardedTo", R.string.BoostingGiftLinkForwardedTo, chat.title));
            } else {
                TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(did);
                text = AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGiftLinkForwardedTo", R.string.BoostingGiftLinkForwardedTo, UserObject.getFirstName(user)));
            }
        }
        AndroidUtilities.runOnUIThread(() -> {
            BulletinFactory bulletinFactory = BulletinFactory.global();
            if (bulletinFactory != null) {
                bulletinFactory.createSimpleBulletinWithIconSize(R.raw.forward, text, 30).show();
            }
        }, 450);
    }

    public static void showBulletinError(TLRPC.TL_error error) {
        BulletinFactory bulletinFactory = BulletinFactory.global();
        if (bulletinFactory == null || error == null || error.text == null) {
            return;
        }
        bulletinFactory.createErrorBulletin(error.text).show();
    }

    public static void showBulletin(FrameLayout container, Theme.ResourcesProvider resourcesProvider, final TLRPC.Chat chat, final boolean isGiveaway) {
        BulletinFactory bulletinFactory = BulletinFactory.of(container, resourcesProvider);
        showBulletin(bulletinFactory, resourcesProvider, chat, isGiveaway);
    }

    public static void showBulletin(final BaseFragment baseFragment, final TLRPC.Chat chat, final boolean isGiveaway) {
        BulletinFactory bulletinFactory = BulletinFactory.of(baseFragment);
        showBulletin(bulletinFactory, baseFragment.getResourceProvider(), chat, isGiveaway);
    }

    private static long roundByFiveMinutes(long dateMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateMs);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.SECOND, 0);

        int minute = calendar.get(Calendar.MINUTE);
        while (minute % 5 != 0) {
            minute++;
        }
        calendar.set(Calendar.MINUTE, minute);
        return calendar.getTimeInMillis();
    }

    public static void showDatePicker(Context context, long currentDate, final AlertsCreator.ScheduleDatePickerDelegate datePickerDelegate, Theme.ResourcesProvider resourcesProvider) {
        final AlertsCreator.ScheduleDatePickerColors datePickerColors = new AlertsCreator.ScheduleDatePickerColors(resourcesProvider);
        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourcesProvider);
        builder.setApplyBottomPadding(false);

        final NumberPicker dayPicker = new NumberPicker(context, resourcesProvider);
        dayPicker.setTextColor(datePickerColors.textColor);
        dayPicker.setTextOffset(dp(10));
        dayPicker.setItemCount(5);
        final NumberPicker hourPicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return formatPluralString("Hours", value);
            }
        };
        hourPicker.setWrapSelectorWheel(true);
        hourPicker.setAllItemsCount(24);
        hourPicker.setItemCount(5);
        hourPicker.setTextColor(datePickerColors.textColor);
        hourPicker.setTextOffset(-dp(10));
        hourPicker.setTag("HOUR");
        final NumberPicker minutePicker = new NumberPicker(context, resourcesProvider) {
            @Override
            protected CharSequence getContentDescription(int value) {
                return formatPluralString("Minutes", value);
            }
        };
        minutePicker.setWrapSelectorWheel(true);
        minutePicker.setAllItemsCount(60);
        minutePicker.setItemCount(5);
        minutePicker.setTextColor(datePickerColors.textColor);
        minutePicker.setTextOffset(-dp(34));

        LinearLayout container = new LinearLayout(context) {

            boolean ignoreLayout = false;
            final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            {
                setWillNotDraw(false);
                paint.setTextSize(dp(20));
                paint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                paint.setColor(datePickerColors.textColor);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                ignoreLayout = true;
                int count;
                if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                    count = 3;
                } else {
                    count = 5;
                }
                dayPicker.setItemCount(count);
                hourPicker.setItemCount(count);
                minutePicker.setItemCount(count);
                dayPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                hourPicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                minutePicker.getLayoutParams().height = dp(NumberPicker.DEFAULT_SIZE_PER_COUNT) * count;
                ignoreLayout = false;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawText(":", hourPicker.getRight() - dp(12), (getHeight() / 2f) - dp(11), paint);
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        container.setOrientation(LinearLayout.VERTICAL);

        FrameLayout titleLayout = new FrameLayout(context);
        container.addView(titleLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 22, 0, 0, 4));

        TextView titleView = new TextView(context);
        titleView.setText(getString("BoostingSelectDateTime", R.string.BoostingSelectDateTime));
        titleView.setTextColor(datePickerColors.textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 12, 0, 0));
        titleView.setOnTouchListener((v, event) -> true);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setWeightSum(1.0f);
        container.addView(linearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 12, 0, 12));

        long currentTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(currentTime);
        int currentYear = calendar.get(Calendar.YEAR);

        TextView buttonTextView = new TextView(context) {
            @Override
            public CharSequence getAccessibilityClassName() {
                return Button.class.getName();
            }
        };

        long maxPeriodMs = BoostRepository.giveawayPeriodMax() * 1000L;
        Calendar calendarMaxPeriod = Calendar.getInstance();
        calendarMaxPeriod.setTimeInMillis(maxPeriodMs);
        int maxDay = calendarMaxPeriod.get(Calendar.DAY_OF_YEAR);
        calendarMaxPeriod.setTimeInMillis(System.currentTimeMillis());
        calendarMaxPeriod.add(Calendar.MILLISECOND, (int) maxPeriodMs);

        int maxHour = calendarMaxPeriod.get(Calendar.HOUR_OF_DAY);
        int maxMinute = calendar.get(Calendar.MINUTE);

        linearLayout.addView(dayPicker, LayoutHelper.createLinear(0, 54 * 5, 0.5f));
        dayPicker.setMinValue(0); //0 for today
        dayPicker.setMaxValue(maxDay - 1);
        dayPicker.setWrapSelectorWheel(false);
        dayPicker.setTag("DAY");
        dayPicker.setFormatter(value -> {
            if (value == 0) {
                return getString("MessageScheduleToday", R.string.MessageScheduleToday);
            } else {
                long date = currentTime + (long) value * 86400000L;
                calendar.setTimeInMillis(date);
                int year = calendar.get(Calendar.YEAR);
                if (year == currentYear) {
                    return LocaleController.getInstance().formatterScheduleDay.format(date);
                } else {
                    return LocaleController.getInstance().formatterScheduleYear.format(date);
                }
            }
        });
        final NumberPicker.OnValueChangeListener onValueChangeListener = (picker, oldVal, newVal) -> {
            try {
                container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {

            }
            if (picker.getTag() != null && (picker.getTag().equals("DAY"))) {
                if (picker.getValue() == picker.getMinValue()) {
                    Calendar calendarCurrent = Calendar.getInstance();
                    calendarCurrent.setTimeInMillis(System.currentTimeMillis());
                    int minHour = calendarCurrent.get(Calendar.HOUR_OF_DAY);
                    int minMinute = calendarCurrent.get(Calendar.MINUTE);
                    int minValueMinute = (minMinute / 5) + 1;
                    if (minValueMinute > 11) {
                        if (minHour == 23) {
                            picker.setMinValue(picker.getMinValue() + 1);
                            hourPicker.setMinValue(0);
                        } else {
                            hourPicker.setMinValue(minHour + 1);
                        }
                        minutePicker.setMinValue(0);
                    } else {
                        hourPicker.setMinValue(minHour);
                        minutePicker.setMinValue(minValueMinute);
                    }
                } else if (picker.getValue() == picker.getMaxValue()) {
                    hourPicker.setMaxValue(maxHour);
                    minutePicker.setMaxValue(Math.min((maxMinute / 5), 11));
                } else {
                    hourPicker.setMinValue(0);
                    minutePicker.setMinValue(0);
                    hourPicker.setMaxValue(23);
                    minutePicker.setMaxValue(11);
                }
            }

            if (picker.getTag() != null && picker.getTag().equals("HOUR") && dayPicker.getValue() == dayPicker.getMinValue()) {
                if (picker.getValue() == picker.getMinValue()) {
                    Calendar calendarCurrent = Calendar.getInstance();
                    calendarCurrent.setTimeInMillis(System.currentTimeMillis());
                    int minMinute = calendarCurrent.get(Calendar.MINUTE);
                    int minValueMinute = (minMinute / 5) + 1;
                    if (minValueMinute > 11) {
                        minutePicker.setMinValue(0);
                    } else {
                        minutePicker.setMinValue(minValueMinute);
                    }
                } else {
                    minutePicker.setMinValue(0);
                    minutePicker.setMaxValue(11);
                }
            }
        };
        dayPicker.setOnValueChangedListener(onValueChangeListener);

        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        linearLayout.addView(hourPicker, LayoutHelper.createLinear(0, 54 * 5, 0.2f));
        hourPicker.setFormatter(value -> String.valueOf(value));
        hourPicker.setOnValueChangedListener(onValueChangeListener);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(11);
        minutePicker.setValue(0);
        minutePicker.setFormatter(value -> String.format("%02d", value * 5));
        linearLayout.addView(minutePicker, LayoutHelper.createLinear(0, 54 * 5, 0.3f));
        minutePicker.setOnValueChangedListener(onValueChangeListener);

        if (currentDate > 0) {
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            int days = (int) ((currentDate - calendar.getTimeInMillis()) / (24 * 60 * 60 * 1000));
            calendar.setTimeInMillis(currentDate);
            minutePicker.setValue(calendar.get(Calendar.MINUTE) / 5);
            hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
            dayPicker.setValue(days);
            onValueChangeListener.onValueChange(dayPicker, dayPicker.getValue(), dayPicker.getValue());
            onValueChangeListener.onValueChange(hourPicker, hourPicker.getValue(), hourPicker.getValue());
        }

        buttonTextView.setPadding(dp(34), 0, dp(34), 0);
        buttonTextView.setGravity(Gravity.CENTER);
        buttonTextView.setTextColor(datePickerColors.buttonTextColor);
        buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        buttonTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        buttonTextView.setBackground(Theme.AdaptiveRipple.filledRect(datePickerColors.buttonBackgroundColor, 8));
        buttonTextView.setText(getString("BoostingConfirm", R.string.BoostingConfirm));
        container.addView(buttonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM, 16, 15, 16, 16));
        buttonTextView.setOnClickListener(v -> {
            calendar.setTimeInMillis(System.currentTimeMillis() + (long) dayPicker.getValue() * 24 * 3600 * 1000);
            calendar.set(Calendar.HOUR_OF_DAY, hourPicker.getValue());
            calendar.set(Calendar.MINUTE, minutePicker.getValue() * 5);
            datePickerDelegate.didSelectDate(true, (int) (calendar.getTimeInMillis() / 1000));
            builder.getDismissRunnable().run();
        });

        builder.setCustomView(container);
        BottomSheet bottomSheet = builder.show();
        bottomSheet.setBackgroundColor(datePickerColors.backgroundColor);
        bottomSheet.fixNavigationBar(datePickerColors.backgroundColor);
        AndroidUtilities.setLightStatusBar(bottomSheet.getWindow(), ColorUtils.calculateLuminance(datePickerColors.backgroundColor) > 0.7f);
    }

    public static void showUnsavedChanges(int type, Context context, Theme.ResourcesProvider resourcesProvider, Runnable onApply, Runnable onDiscard) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("UnsavedChanges", R.string.UnsavedChanges));
        String text;
        switch (type) {
            case TYPE_USER:
                text = getString("BoostingApplyChangesUsers", R.string.BoostingApplyChangesUsers);
                break;
            case TYPE_CHANNEL:
                text = getString("BoostingApplyChangesChannels", R.string.BoostingApplyChangesChannels);
                break;
            case TYPE_COUNTRY:
                text = getString("BoostingApplyChangesCountries", R.string.BoostingApplyChangesCountries);
                break;
            default:
                text = "";
        }
        builder.setMessage(text);
        builder.setPositiveButton(getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> {
            onApply.run();
        });
        builder.setNegativeButton(getString("Discard", R.string.Discard), (dialogInterface, i) -> {
            onDiscard.run();
        });
        builder.show();
    }

    public static boolean checkReduceUsers(Context context, Theme.ResourcesProvider resourcesProvider, List<TLRPC.TL_premiumGiftCodeOption> list, TLRPC.TL_premiumGiftCodeOption selected) {
        if (selected.store_product == null) {
            List<Integer> result = new ArrayList<>();
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                if (item.months == selected.months && item.store_product != null) {
                    result.add(item.users);
                }
            }

            String downTo = TextUtils.join(", ", result);
            int current = selected.users;

            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(getString("BoostingReduceQuantity", R.string.BoostingReduceQuantity));
            builder.setMessage(replaceTags(formatString("BoostingReduceUsersText", R.string.BoostingReduceUsersText, current, downTo)));
            builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {

            });
            builder.show();
            return true;
        }
        return false;
    }

    public static boolean checkReduceQuantity(Context context, Theme.ResourcesProvider resourcesProvider, List<TLRPC.TL_premiumGiftCodeOption> list, TLRPC.TL_premiumGiftCodeOption selected, Utilities.Callback<TLRPC.TL_premiumGiftCodeOption> onSuccess) {
        if (selected.store_product == null) {
            List<TLRPC.TL_premiumGiftCodeOption> result = new ArrayList<>();
            for (TLRPC.TL_premiumGiftCodeOption item : list) {
                if (item.months == selected.months && item.store_product != null) {
                    result.add(item);
                }
            }
            TLRPC.TL_premiumGiftCodeOption suggestion = result.get(0);

            for (TLRPC.TL_premiumGiftCodeOption option : result) {
                if (selected.users > option.users && option.users > suggestion.users) {
                    suggestion = option;
                }
            }

            final TLRPC.TL_premiumGiftCodeOption finalSuggestion = suggestion;

            String months = LocaleController.formatPluralString("GiftMonths", suggestion.months);
            int current = selected.users;
            int downTo = suggestion.users;
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(getString("BoostingReduceQuantity", R.string.BoostingReduceQuantity));
            builder.setMessage(replaceTags(formatString("BoostingReduceQuantityText", R.string.BoostingReduceQuantityText, current, months, downTo)));
            builder.setPositiveButton(getString("Reduce", R.string.Reduce), (dialogInterface, i) -> onSuccess.run(finalSuggestion));
            builder.setNegativeButton(getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {

            });
            builder.show();
            return true;
        }
        return false;
    }

    public static void showAbout(long chatId, long msgDate, TLRPC.TL_payments_giveawayInfo giveawayInfo, TLRPC.TL_messageMediaGiveaway giveaway, Context context, Theme.ResourcesProvider resourcesProvider) {
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-chatId);
        String from = chat != null ? chat.title : "";
        int quantity = giveaway.quantity;
        String months = formatPluralString("BoldMonths", giveaway.months);
        String endDate = LocaleController.getInstance().formatterGiveawayMonthDay.format(new Date(giveaway.until_date * 1000L));

        String fromTime = LocaleController.getInstance().formatterDay.format(new Date(giveawayInfo.start_date * 1000L));
        String fromDate = LocaleController.getInstance().formatterGiveawayMonthDayYear.format(new Date(giveawayInfo.start_date * 1000L));
        boolean isSeveralChats = giveaway.channels.size() > 1;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("BoostingGiveAwayAbout", R.string.BoostingGiveAwayAbout));
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksText", quantity, from, quantity, months)));
        stringBuilder.append("\n\n");

        if (giveaway.only_new_subscribers) {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveral", quantity, endDate, quantity, from, giveaway.channels.size() - 1, fromTime, fromDate)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDate", quantity, endDate, quantity, from, fromTime, fromDate)));
            }
        } else {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextSeveral", quantity, endDate, quantity, from, giveaway.channels.size() - 1)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubText", quantity, endDate, quantity, from)));
            }
        }

        stringBuilder.append("\n\n");

        if (giveawayInfo.participating) {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayParticipantMulti", R.string.BoostingGiveawayParticipantMulti, from, giveaway.channels.size() - 1)));
            } else {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayParticipant", R.string.BoostingGiveawayParticipant, from)));
            }
        } else if (giveawayInfo.disallowed_country != null && !giveawayInfo.disallowed_country.isEmpty()) {
            stringBuilder.append(replaceTags(getString("BoostingGiveawayNotEligibleCountry", R.string.BoostingGiveawayNotEligibleCountry)));
        } else if (giveawayInfo.admin_disallowed_chat_id != 0) {
            TLRPC.Chat badChat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(giveawayInfo.admin_disallowed_chat_id);
            String title = badChat != null ? badChat.title : "";
            stringBuilder.append(replaceTags(formatString("BoostingGiveawayNotEligibleAdmin", R.string.BoostingGiveawayNotEligibleAdmin, title)));
        } else if (giveawayInfo.joined_too_early_date != 0) {
            String date = LocaleController.getInstance().formatterGiveawayMonthDayYear.format(new Date(giveawayInfo.joined_too_early_date * 1000L));
            stringBuilder.append(replaceTags(formatString("BoostingGiveawayNotEligible", R.string.BoostingGiveawayNotEligible, date)));
        } else {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayTakePartMulti", R.string.BoostingGiveawayTakePartMulti, from, giveaway.channels.size() - 1, endDate)));
            } else {
                stringBuilder.append(replaceTags(formatString("BoostingGiveawayTakePart", R.string.BoostingGiveawayTakePart, from, endDate)));
            }
        }

        builder.setMessage(stringBuilder);
        builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {

        });
        builder.show();
    }

    public static void showAboutEnd(long chatId, long msgDate, TLRPC.TL_payments_giveawayInfoResults giveawayInfo, TLRPC.TL_messageMediaGiveaway giveaway, Context context, Theme.ResourcesProvider resourcesProvider) {
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-chatId);
        String from = chat != null ? chat.title : "";
        int quantity = giveaway.quantity;
        String months = formatPluralString("BoldMonths", giveaway.months);
        String endDate = LocaleController.getInstance().formatterGiveawayMonthDay.format(new Date(giveaway.until_date * 1000L));

        String fromTime = LocaleController.getInstance().formatterDay.format(new Date(giveawayInfo.start_date * 1000L));
        String fromDate = LocaleController.getInstance().formatterGiveawayMonthDayYear.format(new Date(giveawayInfo.start_date * 1000L));
        boolean isSeveralChats = giveaway.channels.size() > 1;
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("BoostingGiveawayEnd", R.string.BoostingGiveawayEnd));
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

        stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksTextEnd", quantity, from, quantity, months)));
        stringBuilder.append("\n\n");

        if (giveaway.only_new_subscribers) {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateSeveralEnd", quantity, endDate, quantity, from, giveaway.channels.size() - 1, fromTime, fromDate)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextDateEnd", quantity, endDate, quantity, from, fromTime, fromDate)));
            }
        } else {
            if (isSeveralChats) {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextSeveralEnd", quantity, endDate, quantity, from, giveaway.channels.size() - 1)));
            } else {
                stringBuilder.append(replaceTags(formatPluralString("BoostingGiveawayHowItWorksSubTextEnd", quantity, endDate, quantity, from)));
            }
        }

        stringBuilder.append(" ");
        if (giveawayInfo.activated_count > 0) {
            stringBuilder.append(replaceTags(formatString("BoostingGiveawayUsedLinks", R.string.BoostingGiveawayUsedLinks, giveawayInfo.activated_count)));
        }
        stringBuilder.append("\n\n");

        if (giveawayInfo.refunded) {
            String str = getString("BoostingGiveawayCanceledByPayment", R.string.BoostingGiveawayCanceledByPayment);
            TextView bottomTextView = new TextView(context);
            bottomTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            bottomTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            bottomTextView.setGravity(Gravity.CENTER);
            bottomTextView.setText(str);
            bottomTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider));
            bottomTextView.setBackground(Theme.createRoundRectDrawable(dp(10), dp(10), Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), 0.1f)));
            bottomTextView.setPadding(0, dp(12), 0, dp(12));
            builder.addBottomView(bottomTextView);
            builder.setMessage(stringBuilder);
            builder.setPositiveButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

            });
        } else {
            if (giveawayInfo.winner) {
                stringBuilder.append(getString("BoostingGiveawayYouWon", R.string.BoostingGiveawayYouWon));
                builder.setMessage(stringBuilder);
                builder.setPositiveButton(getString("BoostingGiveawayViewPrize", R.string.BoostingGiveawayViewPrize), (dialogInterface, i) -> {
                    BaseFragment fragment = LaunchActivity.getLastFragment();
                    if (fragment == null) {
                        return;
                    }
                    GiftInfoBottomSheet.show(fragment, giveawayInfo.gift_code_slug);
                });
                builder.setNegativeButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

                });
            } else {
                stringBuilder.append(getString("BoostingGiveawayYouNotWon", R.string.BoostingGiveawayYouNotWon));
                builder.setMessage(stringBuilder);
                builder.setPositiveButton(getString("Close", R.string.Close), (dialogInterface, i) -> {

                });
            }
        }

        builder.show();
    }

    public static void showPrivateChannelAlert(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onCanceled) {
        final AtomicBoolean isAddButtonClicked = new AtomicBoolean(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(getString("BoostingGiveawayPrivateChannel", R.string.BoostingGiveawayPrivateChannel));
        builder.setMessage(getString("BoostingGiveawayPrivateChannelWarning", R.string.BoostingGiveawayPrivateChannelWarning));
        builder.setPositiveButton(getString("Add", R.string.Add), (dialogInterface, i) -> {
            isAddButtonClicked.set(true);
        });
        builder.setNegativeButton(getString("Cancel", R.string.Cancel), (dialogInterface, i) -> {

        });
        builder.setOnDismissListener(dialog -> {
            if (!isAddButtonClicked.get()) {
                onCanceled.run();
            }
        });
        builder.show();
    }

    public static void openGiveAwayStatusDialog(MessageObject messageObject, Browser.Progress progress, Context context, Theme.ResourcesProvider resourcesProvider) {
        final AtomicBoolean isCanceled = new AtomicBoolean(false);
        progress.init();
        progress.onCancel(() -> isCanceled.set(true));
        final TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
        final long chatId = messageObject.getFromChatId();
        final long msgDate = messageObject.messageOwner.date * 1000L;
        BoostRepository.getGiveawayInfo(messageObject, result -> {
            if (isCanceled.get()) {
                return;
            }
            progress.end();
            if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                showAbout(chatId, msgDate, giveawayInfo, giveaway, context, resourcesProvider);
            } else if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                TLRPC.TL_payments_giveawayInfoResults giveawayInfoResults = (TLRPC.TL_payments_giveawayInfoResults) result;
                showAboutEnd(chatId, msgDate, giveawayInfoResults, giveaway, context, resourcesProvider);
            }
        }, error -> {
            if (isCanceled.get()) {
                return;
            }
            progress.end();
        });
    }

    public static void showBulletinAbout(MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        BoostRepository.getGiveawayInfo(messageObject, result -> {
            final TLRPC.TL_messageMediaGiveaway giveaway = (TLRPC.TL_messageMediaGiveaway) messageObject.messageOwner.media;
            final long chatId = messageObject.getFromChatId();
            final long msgDate = messageObject.messageOwner.date * 1000L;
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment == null) {
                return;
            }
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), fragment.getResourceProvider());

            if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                layout.setAnimation(R.raw.chats_infotip, 30, 30);
                layout.textView.setText(LocaleController.getString("BoostingGiveawayShortStatusEnded", R.string.BoostingGiveawayShortStatusEnded));
            } else if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                if (giveawayInfo.participating) {
                    layout.setAnimation(R.raw.forward, 30, 30);
                    layout.textView.setText(LocaleController.getString("BoostingGiveawayShortStatusParticipating", R.string.BoostingGiveawayShortStatusParticipating));
                } else {
                    layout.setAnimation(R.raw.chats_infotip, 30, 30);
                    layout.textView.setText(LocaleController.getString("BoostingGiveawayShortStatusNotParticipating", R.string.BoostingGiveawayShortStatusNotParticipating));
                }
            }

            layout.textView.setSingleLine(false);
            layout.textView.setMaxLines(2);

            layout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, fragment.getResourceProvider())
                    .setText(LocaleController.getString("LearnMore", R.string.LearnMore))
                    .setUndoAction(() -> {
                        if (result instanceof TLRPC.TL_payments_giveawayInfo) {
                            TLRPC.TL_payments_giveawayInfo giveawayInfo = (TLRPC.TL_payments_giveawayInfo) result;
                            showAbout(chatId, msgDate, giveawayInfo, giveaway, fragment.getParentActivity(), fragment.getResourceProvider());
                        } else if (result instanceof TLRPC.TL_payments_giveawayInfoResults) {
                            TLRPC.TL_payments_giveawayInfoResults giveawayInfoResults = (TLRPC.TL_payments_giveawayInfoResults) result;
                            showAboutEnd(chatId, msgDate, giveawayInfoResults, giveaway, fragment.getParentActivity(), fragment.getResourceProvider());
                        }
                    }));
            Bulletin.make(fragment, layout, Bulletin.DURATION_LONG).show();
        }, error -> {

        });
    }

    public static void showMoreBoostsNeeded(long dialogId) {
        TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-dialogId);
        BaseFragment baseFragment = LaunchActivity.getLastFragment();
        if (baseFragment == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getContext(), baseFragment.getResourceProvider());
        builder.setTitle(LocaleController.getString("BoostingMoreBoostsNeeded", R.string.BoostingMoreBoostsNeeded));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("BoostingGetMoreBoostByGifting", R.string.BoostingGetMoreBoostByGifting, chat.title)));
        builder.setPositiveButton(getString("OK", R.string.OK), (dialogInterface, i) -> {

        });
        builder.show();
    }

    public static void showFloodWait(int time) {
        BaseFragment baseFragment = LaunchActivity.getLastFragment();
        if (baseFragment == null) {
            return;
        }
        String timeString;
        if (time < 60) {
            timeString = LocaleController.formatPluralString("Seconds", time);
        } else if (time < 60 * 60) {
            timeString = LocaleController.formatPluralString("Minutes", time / 60);
        } else if (time / 60 / 60 > 2) {
            timeString = LocaleController.formatPluralString("Hours", time / 60 / 60);
        } else {
            timeString = LocaleController.formatPluralString("Hours", time / 60 / 60) + " " + LocaleController.formatPluralString("Minutes", time % 60);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(baseFragment.getContext(), baseFragment.getResourceProvider());
        builder.setTitle(LocaleController.getString("CantBoostTooOften", R.string.CantBoostTooOften));
        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("CantBoostTooOftenDescription", R.string.CantBoostTooOftenDescription, timeString)));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            dialog.dismiss();
        });
        builder.show();
    }
}
