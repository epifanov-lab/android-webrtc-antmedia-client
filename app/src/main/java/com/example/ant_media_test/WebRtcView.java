package com.example.ant_media_test;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.reactivestreams.Processor;
import org.webrtc.SurfaceViewRenderer;

import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;

public class WebRtcView extends FrameLayout {

  private SurfaceViewRenderer renderer;

  private boolean isHoster;
  private String streamId;
  private Flux<String> wssReceiver;
  private DirectProcessor<String> wssSender;

  public WebRtcView(@NonNull Context context) {
    this(context, null);
  }

  public WebRtcView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public WebRtcView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    renderer = (SurfaceViewRenderer) getChildAt(0);
  }

  public void config(boolean isHoster, String streamId, Flux<String> wssReceiver, DirectProcessor<String> wssSender) {
    this.isHoster = isHoster;
    this.streamId = streamId;
    this.wssReceiver = wssReceiver;
    this.wssSender = wssSender;
  }

}
