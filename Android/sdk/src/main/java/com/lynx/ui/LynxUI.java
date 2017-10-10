// Copyright 2017 The Lynx Authors. All rights reserved.
package com.lynx.ui;

import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import com.lynx.base.Position;
import com.lynx.base.Size;
import com.lynx.base.Style;
import com.lynx.core.base.LynxEvent;
import com.lynx.core.base.LynxObject;
import com.lynx.core.impl.RenderImplInterface;
import com.lynx.core.impl.RenderObjectAttr;
import com.lynx.core.impl.RenderObjectImpl;
import com.lynx.core.touch.TouchEventInfo;
import com.lynx.core.touch.TouchTarget;
import com.lynx.core.touch.gesture.GestureEventInfo;
import com.lynx.core.tree.LynxUIAction;
import com.lynx.ui.anim.AnimDriver;
import com.lynx.ui.anim.AnimProperties;
import com.lynx.ui.body.LynxUIBody;
import com.lynx.ui.drawable.UIBackgroundDrawable;
import com.lynx.ui.event.TGEventEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.view.View.LAYER_TYPE_HARDWARE;
import static android.view.View.LAYER_TYPE_NONE;
import static android.view.View.LAYER_TYPE_SOFTWARE;
import static com.lynx.core.impl.RenderObjectAttr.ANIMATE_PROPS;
import static com.lynx.ui.LynxUIFactory.UI_TYPE_CELLVIEW;
import static com.lynx.ui.LynxUIFactory.UI_TYPE_LABEL;

public abstract class LynxUI<T extends View> implements RenderImplInterface, TouchTarget {

    public final static String ATTR_STOP_PROPAGATION = "stop-propagation";
    public final static String ATTR_CAPTURE = "capture";
    public final static String ATTR_PREVENT_DEFAULT = "prevent-default";

    protected T mView;
    protected RenderObjectImpl mRenderObjectImpl;

    // Animator
    private List<AnimDriver> mAnimDrivers;
    private AnimProperties mOldAnimProperties;
    private UIBackgroundDrawable mBgDrawable;

    // Touch
    private TGEventEmitter mTGEventEmitter;
    private boolean mStopPropagationEnable = false;
    private boolean mPreventDefaultEnable = false;
    private boolean mCaptureEnable = false;

    public LynxUI(Context context) {
        initialize();
        mView = createView(context);
        mBgDrawable = new UIBackgroundDrawable();
        mTGEventEmitter = new TGEventEmitter(this);
    }

    public void initialize() {
    }

    public LynxUI(Context context, RenderObjectImpl impl) {
        this(context);
        bindData(impl);
    }

    protected abstract T createView(Context context);

    @Override
    public void insertChild(RenderObjectImpl child, int index) {}

    @Override
    public void removeChild(RenderObjectImpl child) {}

    @Override
    public void requestLayout() {
        mView.requestLayout();
    }

    @Override
    public void setText(String text) {}

    @Override
    public void setPosition(Position position) {
        mBgDrawable.updateBounds(position);
        if (mView.getLayoutParams() != null) {
            mView.getLayoutParams().width = position.getWidth();
            mView.getLayoutParams().height = position.getHeight();
        }
        requestLayout();
    }

    @Override
    public void setAttribute(String key, String value) {
        switch (key) {
            case ATTR_CAPTURE:
                mCaptureEnable = Boolean.valueOf(value);
                break;
            case ATTR_PREVENT_DEFAULT:
                mPreventDefaultEnable = Boolean.valueOf(value);
                break;
            case ATTR_STOP_PROPAGATION:
                mStopPropagationEnable = Boolean.valueOf(value);
                break;
            default:
                break;
        }
    }

    @Override
    public void updateStyle(Style style) {
        if (style == null
                || mRenderObjectImpl.getRenderObjectType() == UI_TYPE_CELLVIEW)
            return;

        setBackground(style);
        setAlpha((float) (style.mOpacity / 255f));
        setLayerType(style);
        setDisplayType(style);

        mView.requestLayout();
        mView.invalidate();
    }

    @Override
    public void setSize(Size size) {

    }

    @Override
    public void addEventListener(String event) {
        mTGEventEmitter.setEnable(event, true);
    }

    @Override
    public void removeEventListener(String event) {
        mTGEventEmitter.setEnable(event, false);
    }

    @Override
    public void setData(int attr, Object param) {
        if(attr == ANIMATE_PROPS.value()) {
            startAnimWithCallback((LynxObject)param);
        }
    }

    public void setBackground(Style style) {
        mBgDrawable.updateStyle(style);
        if (mView.getBackground() == mBgDrawable) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mView.setBackground(mBgDrawable);
        } else {
            mView.setBackgroundDrawable(mBgDrawable);
        }
    }

    protected void setAlpha(float alpha) {
        if (alpha != mView.getAlpha()) {
            mView.setAlpha(alpha);
        }
    }

    protected void setLayerType(Style style) {
        // TODO 加上box-shadow
//        if (!mView.isHardwareAccelerated() && style.mBorderRadius > 0 && style.mCssBoxShadow == null) {
        if (!mView.isHardwareAccelerated()
                && style.mBorderRadius > 0
                && mView.getLayerType() != LAYER_TYPE_HARDWARE) {
            mView.setLayerType(LAYER_TYPE_HARDWARE, null);
        } else if (mView.getLayerType() != LAYER_TYPE_NONE){
            mView.setLayerType(LAYER_TYPE_NONE, null);
        }
    }

    protected void setDisplayType(Style style) {
        if (style.mDisplayType == Style.CSS_DISPLAY_NONE
                || style.mVisible == Style.CSS_HIDDEN
                && mView.getVisibility() != View.INVISIBLE) {
            mView.setVisibility(View.INVISIBLE);
        } else if (mView.getVisibility() != View.VISIBLE){
            mView.setVisibility(View.VISIBLE);
        }
    }

    public void layout() {
        if (mRenderObjectImpl == null) {
            mView.layout(0, 0, 0, 0);
        } else {
            mView.layout(mRenderObjectImpl.getPosition().mLeft,
                    mRenderObjectImpl.getPosition().mTop,
                    mRenderObjectImpl.getPosition().mRight,
                    mRenderObjectImpl.getPosition().mBottom);
        }
    }

    public void measure() {

        setLayoutParamsInternal(mRenderObjectImpl.getParent());

        int width = mRenderObjectImpl == null ?
                0 : mRenderObjectImpl.getPosition().getWidth();
        int height = mRenderObjectImpl == null ?
                0 : mRenderObjectImpl.getPosition().getHeight();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(
                width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(
                height, View.MeasureSpec.EXACTLY);
        mView.measure(widthSpec, heightSpec);
    }

    public void bindData(RenderObjectImpl impl) {
        impl.setUI(this);
        mRenderObjectImpl = impl;
        updateStyle(impl.getStyle());
        setSize(impl.getSize());
        setPosition(impl.getPosition());
        addEvents(impl.getEvents());
        setAttribution(impl.getAttribution());
        updateLayoutParams(mView.getLayoutParams());
        setLayoutParamsInternal(mRenderObjectImpl.getParent());
        setText(mRenderObjectImpl.getText());
    }

    public void unbindData() {
        resetStyle();
        resetEvent(mRenderObjectImpl.getEvents());
        mRenderObjectImpl.setUI(null);
        mRenderObjectImpl = null;
    }

    /**
     * Setting the correct LayoutParams for Android View so that when
     * other parent View want to get the MeasureSize of this View through
     * calling {@link View#measure(int, int)}, the validate MeasureSize
     * can be achieved.
     *
     * @param parent generate LayoutParams from ui of parent
     */
    private void setLayoutParamsInternal(RenderObjectImpl parent) {
        if (parent != null
                && parent.hasUI()
                && parent.getUI() instanceof LynxUIGroup) {
            LynxUIGroup parentUI = (LynxUIGroup) parent.getUI();
            ViewGroup.LayoutParams curParams =
                    mView.getLayoutParams();
            ViewGroup.LayoutParams generatePrams =
                    parentUI.generateLayoutParams(curParams);
            if (curParams != generatePrams) {
                updateLayoutParams(generatePrams);
            }
        } else if (parent != null
                && parent.getRenderObjectType() == LynxUIFactory.UI_TYPE_LIST_SHADOW) {
            RenderObjectImpl grandParent = parent.getParent();
            setLayoutParamsInternal(grandParent);
        }
    }

    /**
     * Update the validate size to LayoutParams of Android View.
     *
     * @param params params to be set
     */
    public void updateLayoutParams(ViewGroup.LayoutParams params) {
        if (params == null) {
            return;
        }
        int width = mRenderObjectImpl.getPosition().getWidth();
        int height = mRenderObjectImpl.getPosition().getHeight();
        params.width = width;
        params.height = height;
        if (params != mView.getLayoutParams()) {
            mView.setLayoutParams(params);
        }
    }

    protected void setAttribution(Map<String, String> attribution) {
        if (attribution != null) {
            for (String key : attribution.keySet()) {
                setAttribute(key, attribution.get(key));
            }
        }
    }

    protected void addEvents(List events) {
        if (events.isEmpty())
            return;
        for (Object event : events) {
            addEventListener((String) event);
        }
    }

    protected void resetEvent(List events) {
        if (events.isEmpty())
            return;
        for (Object event : events) {
            removeEventListener((String) event);
        }
    }

    protected void resetStyle() {
    }

    // TODO: 17/8/8 实现接口
    public void startAnimWithCallback(final LynxObject properties) {

        AnimProperties animProperties = AnimProperties.createFrom(properties);

        AnimDriver driver = AnimDriver.create(this, (String)properties.getProperty("name"), mOldAnimProperties, animProperties);
        driver.startAnim();
        mOldAnimProperties = animProperties;

        // 加入动画队列，方便stop时进行cancel
        if (mAnimDrivers == null) {
            mAnimDrivers = new ArrayList<>();
        }
        mAnimDrivers.add(driver);
    }

    // TODO: 17/8/8 实现接口
    public void stopAnim() {
        mOldAnimProperties = null;
        if (mAnimDrivers == null) {
            return;
        }
        for (AnimDriver driver : mAnimDrivers) {
            driver.stopAnim();
        }
    }

    public void postEvent(String eventName) {
        postEvent(eventName, null);
    }

    public void postEvent(final String eventName, final LynxEvent event) {
        if (mRenderObjectImpl != null && getRootUI() != null) {
            getRootUI().collect(new LynxUIAction.UnorderedAction(mRenderObjectImpl) {
                @Override
                public void doAction() {
                    mTarget.dispatchEvent(eventName, new Object[]{event});
                }

                @Override
                public String key() {
                    return eventName;
                }
            });
        }
    }

    public void updateData(final RenderObjectAttr key, final Object params) {
        if (mRenderObjectImpl != null && getRootUI() != null) {
            getRootUI().collect(new LynxUIAction.UnorderedAction(mRenderObjectImpl) {
                @Override
                public String key() {
                    return key.name();
                }

                @Override
                public void doAction() {
                    mTarget.updateData(key, params);
                }
            });
        }
    }

    public View getView() {
        return mView;
    }

    public RenderObjectImpl getRenderObjectImpl() {
        return mRenderObjectImpl;
    }

    public LynxUIBody getRootUI() {
        if (mRenderObjectImpl != null
                && mRenderObjectImpl.getRootRenderObjectImpl() != null) {
            LynxUI root = mRenderObjectImpl.getRootRenderObjectImpl().getUI();
            return (LynxUIBody) root;
        }
        return null;
    }

    // TouchTarget & Gesture Target Implement
    @Override
    public void performTouch(TouchEventInfo info) {
        if (!isCaptureEnable()) {
            mTGEventEmitter.emitTouchEvent(info);
        }
    }

    @Override
    public void performGesture(GestureEventInfo info) {
        mTGEventEmitter.emitGestureEvent(info);
    }

    @Override
    public void onCapturingTouchEvent(TouchEventInfo info) {
        if (isCaptureEnable()) {
            mTGEventEmitter.emitTouchEvent(info);
        }
    }

    @Override
    public boolean isPreventDefault() {
        return mPreventDefaultEnable;
    }

    @Override
    public boolean isStopPropagation() {
        return mStopPropagationEnable;
    }

    @Override
    public boolean isCaptureEnable() {
        return mCaptureEnable;
    }
}
