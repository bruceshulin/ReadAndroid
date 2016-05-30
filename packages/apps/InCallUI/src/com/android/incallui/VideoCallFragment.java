/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.incallui;

import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Debug;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;

/**
 * Fragment containing video calling surfaces.
 */
public class VideoCallFragment extends BaseFragment<VideoCallPresenter,
        VideoCallPresenter.VideoCallUi> implements VideoCallPresenter.VideoCallUi {

    /**
     * Used to indicate that the surface dimensions are not set.
     */
    private static final int DIMENSIONS_NOT_SET = -1;

    /**
     * Surface ID for the display surface.
     */
    public static final int SURFACE_DISPLAY = 1;

    /**
     * Surface ID for the preview surface.
     */
    public static final int SURFACE_PREVIEW = 2;

    // Static storage used to retain the video surfaces across Activity restart.
    // TextureViews are not parcelable, so it is not possible to store them in the saved state.
    private static boolean sVideoSurfacesInUse = false;
    private static VideoCallSurface sPreviewSurface = null;
    private static VideoCallSurface sDisplaySurface = null;

    /**
     * {@link ViewStub} holding the video call surfaces.  This is the parent for the
     * {@link VideoCallFragment}.  Used to ensure that the video surfaces are only inflated when
     * required.
     */
    private ViewStub mVideoViewsStub;

    /**
     * Inflated view containing the video call surfaces represented by the {@link ViewStub}.
     */
    private View mVideoViews;

    /**
     * {@code True} when the entering the activity again after a restart due to orientation change.
     */
    private boolean mIsActivityRestart;

    /**
     * {@code True} when the layout of the activity has been completed.
     */
    private boolean mIsLayoutComplete = false;

    /**
     * {@code True} if in landscape mode.
     */
    private boolean mIsLandscape;
    // SPRD: fix bug 419891
    private int displayViewHeight;
    // SPRD: add for bug427890
    private static final boolean DBG = Debug.isDebug();

    /**
     * The width of the surface.
     */
//    private int mWidth = DIMENSIONS_NOT_SET; // SPRD: modify for display surface don't create

    /**
     * The height of the surface.
     */
//    private int mHeight = DIMENSIONS_NOT_SET; // SPRD: modify for display surface don't create

    /**
     * Inner-class representing a {@link TextureView} and its associated {@link SurfaceTexture} and
     * {@link Surface}.  Used to manage the lifecycle of these objects across device orientation
     * changes.
     */
    private class VideoCallSurface implements TextureView.SurfaceTextureListener,
            View.OnClickListener {
        private int mSurfaceId;
        private TextureView mTextureView;
        private SurfaceTexture mSavedSurfaceTexture;
        private Surface mSavedSurface;
        /** SPRD: modify for display surface don't create @{ */
        private int mWidth = DIMENSIONS_NOT_SET;
        private int mHeight = DIMENSIONS_NOT_SET;
        /** @} */

        /**
         * Creates an instance of a {@link VideoCallSurface}.
         *
         * @param surfaceId The surface ID of the surface.
         * @param textureView The {@link TextureView} for the surface.
         */
        public VideoCallSurface(int surfaceId, TextureView textureView) {
            this(surfaceId, textureView, DIMENSIONS_NOT_SET, DIMENSIONS_NOT_SET);
        }

        /**
         * Creates an instance of a {@link VideoCallSurface}.
         *
         * @param surfaceId The surface ID of the surface.
         * @param textureView The {@link TextureView} for the surface.
         * @param width The width of the surface.
         * @param height The height of the surface.
         */
        public VideoCallSurface(int surfaceId, TextureView textureView, int width, int height) {
            mWidth = width;
            mHeight = height;
            mSurfaceId = surfaceId;

            recreateView(textureView);
        }

        /**
         * Recreates a {@link VideoCallSurface} after a device orientation change.  Re-applies the
         * saved {@link SurfaceTexture} to the
         *
         * @param view The {@link TextureView}.
         */
        public void recreateView(TextureView view) {
            mTextureView = view;
            mTextureView.setSurfaceTextureListener(this);
            mTextureView.setOnClickListener(this);

            if (mSavedSurfaceTexture != null) {
                mTextureView.setSurfaceTexture(mSavedSurfaceTexture);
            }
        }

        /**
         * Handles {@link SurfaceTexture} callback to indicate that a {@link SurfaceTexture} has
         * been successfully created.
         *
         * @param surfaceTexture The {@link SurfaceTexture} which has been created.
         * @param width The width of the {@link SurfaceTexture}.
         * @param height The height of the {@link SurfaceTexture}.
         */
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width,
                int height) {
            boolean surfaceCreated;
            // Where there is no saved {@link SurfaceTexture} available, use the newly created one.
            // If a saved {@link SurfaceTexture} is available, we are re-creating after an
            // orientation change.
            if (mSavedSurfaceTexture == null) {
                mSavedSurfaceTexture = surfaceTexture;
                surfaceCreated = createSurface();
            } else {
                // A saved SurfaceTexture was found.
                surfaceCreated = true;
            }

            // Inform presenter that the surface is available.
            if (surfaceCreated) {
                getPresenter().onSurfaceCreated(mSurfaceId);
            }
            // SPRD: modify for surface don't clear in some case.
            Log.i(this, "onSurfaceTextureAvailable->surface:" + mSurfaceId
                    + "  surfaceCreated:" + surfaceCreated
                    + " mSavedSurfaceTexture is null?:"
                    + (mSavedSurfaceTexture == null));
        }

        /**
         * Handles a change in the {@link SurfaceTexture}'s size.
         *
         * @param surfaceTexture The {@link SurfaceTexture}.
         * @param width The new width.
         * @param height The new height.
         */
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width,
                int height) {
            // Not handled
        }

        /**
         * Handles {@link SurfaceTexture} destruct callback, indicating that it has been destroyed.
         *
         * @param surfaceTexture The {@link SurfaceTexture}.
         * @return {@code True} if the {@link TextureView} can release the {@link SurfaceTexture}.
         */
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            /**
             * Destroying the surface texture; inform the presenter so it can null the surfaces.
             */
            /** SPRD: modify for surface don't clear when user press home button
             * @Orig:if (mSavedSurfaceTexture == null) {
             * { */
            Log.i(this, "onSurfaceTextureDestroyed->surface:"+mSurfaceId
                    +" mSavedSurfaceTexture is null?:"+(mSavedSurfaceTexture == null));
            if (mSavedSurfaceTexture != null) {
            /** @} */
                getPresenter().onSurfaceDestroyed(mSurfaceId);
                if (mSavedSurface != null) {
                    mSavedSurface.release();
                    mSavedSurface = null;
                }
                mSavedSurfaceTexture = null; // SPRD: modify for surface don't clear when user press home button.
            }

            // The saved SurfaceTexture will be null if we're shutting down, so we want to
            // return "true" in that case (indicating that TextureView can release the ST).
            return (mSavedSurfaceTexture == null);
        }

        /**
         * Handles {@link SurfaceTexture} update callback.
         * @param surface
         */
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Not Handled
        }

        /**
         * Retrieves the current {@link TextureView}.
         *
         * @return The {@link TextureView}.
         */
        public TextureView getTextureView() {
            return mTextureView;
        }

        /**
         * Called by the user presenter to indicate that the surface is no longer required due to a
         * change in video state.  Releases and clears out the saved surface and surface textures.
         */
        public void setDoneWithSurface() {
            if (mSavedSurface != null) {
                mSavedSurface.release();
                mSavedSurface = null;
            }
            if (mSavedSurfaceTexture != null) {
                mSavedSurfaceTexture.release();
                mSavedSurfaceTexture = null;
            }
        }

        /**
         * Retrieves the saved surface instance.
         *
         * @return The surface.
         */
        public Surface getSurface() {
            return mSavedSurface;
        }

        /**
         * Sets the dimensions of the surface.
         *
         * @param width The width of the surface, in pixels.
         * @param height The height of the surface, in pixels.
         */
        public void setSurfaceDimensions(int width, int height) {
            mWidth = width;
            mHeight = height;

            if (mSavedSurfaceTexture != null) {
                createSurface();
            }
        }

        /**
         * Creates the {@link Surface}, adjusting the {@link SurfaceTexture} buffer size.
         */
        private boolean createSurface() {
            // SPRD: modify for surface don't clear in some case
            Log.i(this, "createSurface->surface:" + mSurfaceId + " mWidth:"
                    + mWidth + " mHeight:" + mHeight
                    + " mSavedSurfaceTexture is null?:"
                    + (mSavedSurfaceTexture == null));

            if (mWidth != DIMENSIONS_NOT_SET && mHeight != DIMENSIONS_NOT_SET &&
                    mSavedSurfaceTexture != null) {

                mSavedSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
                mSavedSurface = new Surface(mSavedSurfaceTexture);
                return true;
            }
            return false;
        }

        /**
         * Handles a user clicking the surface, which is the trigger to toggle the full screen
         * Video UI.
         *
         * @param view The view receiving the click.
         */
        @Override
        public void onClick(View view) {
            getPresenter().onSurfaceClick(mSurfaceId);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIsActivityRestart = sVideoSurfacesInUse;
        //SPRD: add for bug427890
        if(DBG) Log.i(this, "onCreate mIsActivityRestart = " + mIsActivityRestart);
    }

    /**
     * Handles creation of the activity and initialization of the presenter.
     *
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mIsLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        getPresenter().init(getActivity());
        /* SPRD: fix bug 419891 @{ */
        if(mVideoViews != null){
            TextureView displaySurface = (TextureView) mVideoViews.findViewById(R.id.incomingVideo);
            TextureView previewSurface = (TextureView) mVideoViews.findViewById(R.id.previewVideo);
            Point screenSize = getScreenSize();
            setSurfaceSizeAndTranslation(displaySurface, screenSize, previewSurface);
        }
        /* @} */
    }

    /**
     * Handles creation of the fragment view.
     *
     * @param inflater The inflater.
     * @param container The view group containing the fragment.
     * @param savedInstanceState The saved instance state.
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        final View view = inflater.inflate(R.layout.video_call_fragment, container, false);

        // Attempt to center the incoming video view, if it is in the layout.
        final ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Check if the layout includes the incoming video surface -- this will only be the
                // case for a video call.
                /* SPRD: fix bug 419891
                 *  @orig
                 *  View displayVideo = view.findViewById(R.id.incomingVideo);
                 * @{ */
                TextureView displayVideo = (TextureView)view.findViewById(R.id.incomingVideo);
                TextureView previewVideo = (TextureView)view.findViewById(R.id.previewVideo);
                /* @} */
                if (displayVideo != null && previewVideo != null) {
                    /* SPRD: fix bug 419891
                     * @orig
                    centerDisplayView(displayVideo);
                     * @{ */
                    Point screenSize = getScreenSize();
                    setSurfaceSizeAndTranslation(displayVideo, screenSize, previewVideo);
                    /* @} */
                }

                mIsLayoutComplete = true;

                // Remove the listener so we don't continually re-layout.
                ViewTreeObserver observer = view.getViewTreeObserver();
                if (observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(this);
                }
            }
        });

        return view;
    }

    /**
     * Centers the display view vertically for portrait orientation, and horizontally for
     * lanscape orientations.  The view is centered within the available space not occupied by
     * the call card.
     *
     * @param displayVideo The video view to center.
     */
    private void centerDisplayView(View displayVideo) {
        // In a lansdcape layout we need to ensure we horizontally center the view based on whether
        // the layout is left-to-right or right-to-left.
        // In a left-to-right locale, the space for the video view is to the right of the call card
        // so we need to translate it in the +X direction.
        // In a right-to-left locale, the space for the video view is to the left of the call card
        // so we need to translate it in the -X direction.
        final boolean isLayoutRtl = InCallPresenter.isRtl();

        float spaceBesideCallCard = InCallPresenter.getInstance().getSpaceBesideCallCard();
        /** SPRD: VIDEO CALL FEATURE. @{ */
        float spaceBesideCallCard2 = InCallPresenter.getInstance().getSpaceBesideCallCard2();
        int width = getScreenSize().x;
        int height = getScreenSize().y;
        if (displayVideo.getWidth() != 0) {
            width = displayVideo.getWidth();
        }
        if (displayVideo.getHeight() != 0) {
            height = displayVideo.getHeight();
        }
        /** @} */
        if (mIsLandscape) {
            // SPRD: fix bug 391724
            float videoViewTranslation = width / 2
                    - spaceBesideCallCard / 2;
            if (isLayoutRtl) {
                displayVideo.setTranslationX(-videoViewTranslation);
            } else {
                displayVideo.setTranslationX(videoViewTranslation);
            }
        } else {
            /**
             * SPRD: modify videoViewTranslation for Bug383094
             * org:videoViewTranslation = displayVideo.getHeight() / 2 - spaceBesideCallCard / 2;
             * @{
             */
            float videoViewTranslation;
            if (spaceBesideCallCard == 0) {
                videoViewTranslation = height / 2 - spaceBesideCallCard2 / 2;
            } else {
                videoViewTranslation = height / 2 - spaceBesideCallCard / 2;
            }
            /** @} */
            displayVideo.setTranslationY(videoViewTranslation);
        }
    }

    /**
     * After creation of the fragment view, retrieves the required views.
     *
     * @param view The fragment view.
     * @param savedInstanceState The saved instance state.
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVideoViewsStub = (ViewStub) view.findViewById(R.id.videoCallViewsStub);

        // If the surfaces are already in use, we have just changed orientation or otherwise
        // re-created the fragment.  In this case we need to inflate the video call views and
        // restore the surfaces.
        if (sVideoSurfacesInUse) {
            inflateVideoCallViews();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVideoViews != null) {
            mVideoViews.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top,
                        int right, int bottom, int oldLeft, int oldTop,
                        int oldRight, int oldBottom) {
                    float spaceBesideCallCard = InCallPresenter.getInstance()
                             .getSpaceBesideCallCard();
                    if (spaceBesideCallCard > 0) {
                        TextureView displaySurface = (TextureView) mVideoViews
                                .findViewById(R.id.incomingVideo);
                        TextureView previewSurface = (TextureView) mVideoViews
                                .findViewById(R.id.previewVideo);
                        Point screenSize = getScreenSize();
                        setSurfaceSizeAndTranslation(displaySurface,
                                screenSize, previewSurface);
                    }
                    v.removeOnLayoutChangeListener(this);
                    }
            });
        }
    }

    /**
     * Creates the presenter for the {@link VideoCallFragment}.
     * @return The presenter instance.
     */
    @Override
    public VideoCallPresenter createPresenter() {
        /** SPRD: modify for surface don't clear in some case
         * @Orig:return new VideoCallPresenter();
         * { */
        return getPresenter();
        /** @} */
    }

    /**
     * @return The user interface for the presenter, which is this fragment.
     */
    // sprd: modify access permission for conferencecall list
    @Override
    public VideoCallPresenter.VideoCallUi getUi() {
        return this;
    }

    /**
     * Toggles visibility of the video UI.
     *
     * @param show {@code True} if the video surfaces should be shown.
     */
    @Override
    public void showVideoUi(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        getView().setVisibility(visibility);
        if (show) {
            inflateVideoCallViews();
        } else {
            cleanupSurfaces();
        }

        if (mVideoViews != null ) {
            mVideoViews.setVisibility(visibility);
        }
    }

    /**
     * Cleans up the video telephony surfaces.  Used when the presenter indicates a change to an
     * audio-only state.  Since the surfaces are static, it is important to ensure they are cleaned
     * up promptly.
     */
    @Override
    public void cleanupSurfaces() {
        if (sDisplaySurface != null) {
            sDisplaySurface.setDoneWithSurface();
            sDisplaySurface = null;
        }
        if (sPreviewSurface != null) {
            sPreviewSurface.setDoneWithSurface();
            sPreviewSurface = null;
        }
        sVideoSurfacesInUse = false;
    }

    @Override
    public boolean isActivityRestart() {
        return mIsActivityRestart;
    }

    /**
     * @return {@code True} if the display video surface has been created.
     */
    @Override
    public boolean isDisplayVideoSurfaceCreated() {
        return sDisplaySurface != null && sDisplaySurface.getSurface() != null;
    }

    /**
     * @return {@code True} if the preview video surface has been created.
     */
    @Override
    public boolean isPreviewVideoSurfaceCreated() {
        return sPreviewSurface != null && sPreviewSurface.getSurface() != null;
    }

    /**
     * {@link android.view.Surface} on which incoming video for a video call is displayed.
     * {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    @Override
    public Surface getDisplayVideoSurface() {
        return sDisplaySurface == null ? null : sDisplaySurface.getSurface();
    }

    /**
     * {@link android.view.Surface} on which a preview of the outgoing video for a video call is
     * displayed.  {@code Null} until the video views {@link android.view.ViewStub} is inflated.
     */
    @Override
    public Surface getPreviewVideoSurface() {
        return sPreviewSurface == null ? null : sPreviewSurface.getSurface();
    }

    /**
     * Changes the dimensions of the preview surface.  Called when the dimensions change due to a
     * device orientation change.
     *
     * @param width The new width.
     * @param height The new height.
     */
    @Override
    public void setPreviewSize(int width, int height) {
        if (sPreviewSurface != null) {
            TextureView preview = sPreviewSurface.getTextureView();

            if (preview == null ) {
                return;
            }

            ViewGroup.LayoutParams params = preview.getLayoutParams();
            params.width = width;
            params.height = height;
            preview.setLayoutParams(params);

            sPreviewSurface.setSurfaceDimensions(width, height);
        }
    }

    /**
     * Inflates the {@link ViewStub} containing the incoming and outgoing surfaces, if necessary,
     * and creates {@link VideoCallSurface} instances to track the surfaces.
     */
    private void inflateVideoCallViews() {
        if (mVideoViews == null ) {
            mVideoViews = mVideoViewsStub.inflate();
        }

        if (mVideoViews != null) {
            TextureView displaySurface = (TextureView) mVideoViews.findViewById(R.id.incomingVideo);
            /* SPRD: fix bug 419891 @{ */
            TextureView previewSurface = (TextureView) mVideoViews.findViewById(R.id.previewVideo);
            /* @} */

            Point screenSize = getScreenSize();
            // SPRD: fix bug 419891, add previewSurface
            setSurfaceSizeAndTranslation(displaySurface, screenSize, previewSurface);

            if (!sVideoSurfacesInUse) {
                // Where the video surfaces are not already in use (first time creating them),
                // setup new VideoCallSurface instances to track them.
                sDisplaySurface = new VideoCallSurface(SURFACE_DISPLAY,
                        (TextureView) mVideoViews.findViewById(R.id.incomingVideo), screenSize.x,
                        screenSize.y);
                sPreviewSurface = new VideoCallSurface(SURFACE_PREVIEW,
                        (TextureView) mVideoViews.findViewById(R.id.previewVideo));
                //SPRD: add for bug427890
                if(DBG) Log.i(this, "inflateVideoCallViews set sVideoSurfacesInUse true");
                sVideoSurfacesInUse = true;
            } else {
                // In this case, the video surfaces are already in use (we are recreating the
                // Fragment after a destroy/create cycle resulting from a rotation.
                sDisplaySurface.recreateView((TextureView) mVideoViews.findViewById(
                        R.id.incomingVideo));
                sPreviewSurface.recreateView((TextureView) mVideoViews.findViewById(
                        R.id.previewVideo));
            }
        }
    }

    /**
     * Resizes a surface so that it has the same size as the full screen and so that it is
     * centered vertically below the call card.
     *
     * @param textureView The {@link TextureView} to resize and position.
     * @param size The size of the screen.
     */
    //SPRD: fix bug 419891 add previewView
    private void setSurfaceSizeAndTranslation(TextureView displayView, Point size, TextureView previewView) {
        // Set the surface to have that size.
        /* SPRD: fix bug 419891
         * @orig
            ViewGroup.LayoutParams params = textureView.getLayoutParams();
            params.width = size.x;
            params.height = size.y;
            textureView.setLayoutParams(params);
         * @{ */
        ViewGroup.LayoutParams params = displayView.getLayoutParams();
        if(mIsLandscape){
            float spaceBesideCallCard = InCallPresenter.getInstance().getSpaceBesideCallCard();
            if(spaceBesideCallCard != 0){
                params.width = (int)spaceBesideCallCard;
                params.height = params.width*9/11;
            }else{
                params.width = size.x;
                params.height = size.y;
            }
        }else{
            params.width = size.x;
            params.height = size.x*9/11;
        }
        displayView.setLayoutParams(params);

        displayViewHeight = params.height;
        setSurfaceTranslation(displayView, previewView);
        /* @} */

        // It is only possible to center the display view if layout of the views has completed.
        // It is only after layout is complete that the dimensions of the Call Card has been
        // established, which is a prerequisite to centering the view.
        // Incoming video calls will center the view
        if (mIsLayoutComplete && ((mIsLandscape && displayView.getTranslationX() == 0) || (
                !mIsLandscape && displayView.getTranslationY() == 0))) {
            /* SPRD: fix bug 419891 @{ */
            //centerDisplayView(textureView);
            /* @} */
        }
    }

    /**
     * Determines the size of the device screen.
     *
     * @return {@link Point} specifying the width and height of the screen.
     */
    private Point getScreenSize() {
        // Get current screen size.
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        return size;
    }

    @Override
    public boolean isVideoSurfacesInUse() {
        // TODO Auto-generated method stub
        //SPRD:modify false to sVideoSurfacesInUse for bug427488
        return sVideoSurfacesInUse;
    }
    /* SPRD: modify for surface don't clear in some case { */
    @Override
    public VideoCallPresenter getPresenter(){
        return VideoCallPresenter.getInstance();
    }
    /* @} */
    /* SPRD: fix bug 419891 @{ */
    private void setSurfaceTranslation(View displayView, View previewView){
        float spaceBesideCallCard = InCallPresenter.getInstance().getSpaceBesideCallCard();
        float displayVideoTranslation = 0;
        if(mIsLandscape){
            if(mVideoViews != null){
                displayVideoTranslation = (mVideoViews.getHeight() - displayViewHeight)/2;
            }
        }else{
            displayVideoTranslation = spaceBesideCallCard - displayViewHeight;
        }
        if(spaceBesideCallCard != 0 && displayViewHeight != 0 && displayVideoTranslation > 0){
            if(!mIsLandscape){
                displayView.setTranslationY(-displayVideoTranslation);
            }
            previewView.setTranslationY(-displayVideoTranslation);
        }
    }
    /* @} */
}
