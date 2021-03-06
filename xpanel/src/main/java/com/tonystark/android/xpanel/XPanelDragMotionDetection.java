package com.tonystark.android.xpanel;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Tony on 2018/2/15.
 */

public class XPanelDragMotionDetection extends ViewDragHelper.Callback {

    private ViewGroup mDragView;

    private ViewGroup mDragContainer;

    private ViewDragHelper mDragHelper;

    private IXPanelListScrollCtrl mScrollCtrl;

    private Context mContext;

    private boolean isChuttyMode;

    private boolean isCeiling;

    private boolean isOriginState;

    private int mOriginTop;

    private float mKickBackPercent;

    private int mOffsetPixel;

    private boolean isCanFling;

    private boolean isDragUp;

    private boolean isInFling;

    private boolean isInBaseLine;

    private OnXPanelEventListener mOnXPanelEventListener;

    public XPanelDragMotionDetection(ViewGroup dragView, ViewGroup dragContainer, IXPanelListScrollCtrl scrollCtrl) {
        mDragView = dragView;
        mDragContainer = dragContainer;
        mContext = mDragContainer.getContext();
        mScrollCtrl = scrollCtrl;
        mDragHelper = ViewDragHelper.create(mDragContainer, 1.0f, this);
        isOriginState = true;
        mKickBackPercent = 0.5f;
    }

    @Override
    public boolean tryCaptureView(View child, int pointerId) {
        return child == mDragView;
    }

    @Override
    public int clampViewPositionVertical(View child, int top, int dy) {
        int containerHeight = mDragContainer.getMeasuredHeight();

        //resolve base line
        if (dy > 0) {
            //move down
            int currentHeight = containerHeight - top;
            int exposedHeight = containerHeight - mOriginTop;
            if (currentHeight <= exposedHeight) {
                setScrollLock(false);
                isInBaseLine = true;
                return mOriginTop;
            } else {
                isInBaseLine = false;
            }
        } else {
            setScrollLock(true);
            isInBaseLine = false;
        }

        //resolve list can not scroll,fixed height
        if (!mScrollCtrl.canScroll()) {
            int offset = -mDragView.getMeasuredHeight() + containerHeight;
            if (top <= offset) {
                return offset;
            } else {
                return top;
            }
        }

        //resolve list is not fill parent
        if (mDragView.getMeasuredHeight() < containerHeight) {
            int smallestTop = containerHeight - mDragView.getMeasuredHeight();
            if (smallestTop >= 0 && top < smallestTop) {
                return smallestTop;
            }
        } else if (top - dy == 0) {//resolve list drag to container top
            //resolve list could scroll or not
            if (dy > 0) {
                //move down
                if (!mScrollCtrl.isScrollInBegin()) {
                    return 0;
                }
            } else {
                //move up
                if (!mScrollCtrl.isScrollInEnd()) {
                    return 0;
                }
            }
        }

        //resolve drag not out to screen top.
        if (top < 0) {
            return 0;
        }

        return top;
    }

    @Override
    public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
        mOffsetPixel = mDragContainer.getMeasuredHeight() - top;
        isDragUp = dy < 0;

        if (top == mOriginTop) {
            isOriginState = true;
        } else {
            isOriginState = false;
        }

        if (isInFling) {
            onDragEvent(DragEvent.DRAG_FLING, dy);
        } else {
            if (!isInBaseLine) {
                onDragEvent(DragEvent.DRAG_MOVE, dy);
            }
        }

        if (top <= 0 != isCeiling && mOnXPanelEventListener != null) {//call once
            mOnXPanelEventListener.onCeiling(top <= 0);
        }

        isCeiling = top <= 0;
        Log.i("Position", "mOffsetPixel:" + mOffsetPixel +
                " mOriginTop:" + mOriginTop +
                " isCeiling:" + isCeiling +
                " isOriginState:" + isOriginState +
                " isDragUp" + isDragUp);

    }

    @Override
    public void onViewCaptured(View capturedChild, int activePointerId) {
        onDragEvent(DragEvent.DRAG_FINGER_DOWN, 0);
    }

    @Override
    public void onViewReleased(View releasedChild, float xvel, float yvel) {
        onDragEvent(DragEvent.DRAG_FINGER_UP, 0);
        if (isChuttyMode) {
            float threshold = mDragContainer.getMeasuredHeight() * (mKickBackPercent);

            if (mOffsetPixel >= threshold) {//before touch the captured view ,view state is origin state.
                int top = Math.max(mDragContainer.getMeasuredHeight() - mDragView.getMeasuredHeight(), 0);
                mDragHelper.settleCapturedViewAt(0, top);
            } else {
                mDragHelper.settleCapturedViewAt(0, mOriginTop);
            }
        }
        fling();
        ViewCompat.postInvalidateOnAnimation(mDragContainer);
    }

    private void fling() {
        if (!isCanFling || isChuttyMode) {
            return;
        }
        if (isCeiling && !mScrollCtrl.isScrollInBegin()) {
            return;
        }
        isInFling = true;
        int minTop = mDragContainer.getMeasuredHeight() - mDragView.getMeasuredHeight();
        int maxTop = mOriginTop;
        mDragHelper.flingCapturedView(0, minTop, 0, maxTop);
    }

    @Override
    public void onViewDragStateChanged(int state) {
        if (state == ViewDragHelper.STATE_IDLE) {
            mDragHelper.abort();
            isInFling = false;
            if (mOnXPanelEventListener != null) {
                int offset = getOffsetPixel();
                mOnXPanelEventListener.onDrag(DragEvent.DRAG_STOP, offset, 0);
            }
        }
    }

    private void setScrollLock(boolean isScroll) {
        if (!mScrollCtrl.isMeasureAll()) {
            mScrollCtrl.setScrollLock(isScroll);
        }
    }

    @Override
    public int getViewVerticalDragRange(View child) {
        return child.getMeasuredHeight();
    }

    public int getOffsetPixel() {
        return mOffsetPixel;
    }

    public boolean isOriginState() {
        return isOriginState;
    }

    /**
     * set the kick back percent when the chutty mode is true.
     *
     * @param kickBackPercent range in 0 ~ 1.
     */
    public void setKickBackPercent(float kickBackPercent) {
        if (kickBackPercent < 0) {
            kickBackPercent = 0.01f;
        }
        if (kickBackPercent > 1) {
            kickBackPercent = 1;
        }
        mKickBackPercent = kickBackPercent;
    }

    public void setOriginTop(int originTop) {
        mOriginTop = originTop;
        mOffsetPixel = mOriginTop;
    }

    public int getOriginTop() {
        return mOriginTop;
    }

    public void setChuttyMode(boolean chuttyMode) {
        isChuttyMode = chuttyMode;
    }

    public ViewDragHelper getDragHelper() {
        return mDragHelper;
    }

    public void setCanFling(boolean canFling) {
        isCanFling = canFling;
    }

    public boolean isCeiling() {
        return isCeiling;
    }

    public boolean isInBaseLine() {
        return isInBaseLine;
    }

    public void setOnXPanelEventListener(OnXPanelEventListener onXPanelEventListener) {
        mOnXPanelEventListener = onXPanelEventListener;
    }

    public boolean shouldInterceptTouchEvent(MotionEvent ev) {
        return getDragHelper().shouldInterceptTouchEvent(ev);
    }

    public boolean processTouchEvent(MotionEvent event) {
        getDragHelper().processTouchEvent(event);
        return true;
    }

    private void onDragEvent(int event, int dy) {
        if (mOnXPanelEventListener != null) {
            mOnXPanelEventListener.onDrag(event, getOffsetPixel(), dy);
        }
    }

    public interface DragEvent {
        /**
         * When you drag moving
         */
        int DRAG_MOVE = 1;
        /**
         * Drag stop
         */
        int DRAG_STOP = 2;
        /**
         * When not touch but in scrolling.
         */
        int DRAG_FLING = 3;
        /**
         * When you touch the drag view.
         */
        int DRAG_FINGER_UP = 4;
        /**
         * WHen you release the drag view.
         */
        int DRAG_FINGER_DOWN = 5;
    }

    public interface OnXPanelEventListener {
        /**
         * When user drag the view or fling.
         *
         * @param dragEvent drag motion.
         * @param offset    offset from original point.
         * @param dy        the move pixel in your moving this time.
         */
        void onDrag(int dragEvent, int offset, int dy);

        /**
         * When the drag view touch ceil,top may be small than or equal 0.
         *
         * @param isCeiling true is in ceiling.
         */
        void onCeiling(boolean isCeiling);
    }
}
