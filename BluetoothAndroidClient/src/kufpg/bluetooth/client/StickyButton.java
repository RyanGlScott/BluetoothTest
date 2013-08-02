package kufpg.bluetooth.client;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

/**
 * A {@link Button} that "sticks" when the user clicks it. That is, this {@code Button}'s
 * {@link OnClickListener} will be called once when the user initially clicks the button, and
 * any subsequent clicks will have no effect until {@link #unstick()} is called.
 */
public class StickyButton extends Button {
	/** The listener that is called upon an initial button click. */
	private OnClickListener mOnClickListener;
	
	/** If {@code StickyButton} is stuck, clicks will not call {@link View.OnClickListener
	 * OnClickListener}'s {@link View.OnClickListener#onClick(View) onClick(View)} method. */
	private boolean mIsStuck = false;
	
	/** Used to prevent the {@link Button} from queuing clicks, even if the {@code Button}
	 * is not enabled. */
	private final ReentrantLock mLock = new ReentrantLock(true);
	
	/** {@link #mLock}'s condition. */
	private final Condition mLockInEffect = mLock.newCondition();

	public StickyButton(Context context) {
		super(context);
		init();
	}

	public StickyButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public StickyButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init() {
		super.setOnClickListener(new OnClickListener() {
			@Override
			public final void onClick(View v) {
				click(true);
			}
		});
	}
	
	@Override
	public void setOnClickListener(OnClickListener listener) {
		mOnClickListener = listener;
	}
	
	/** 
	 * Returns whether a click will call this {@link Button}'s {@link View.OnClickListener
	 * OnClickListener}.
	 * @return {@code true} if the button will call {@link View.OnClickListener#onClick(View)
	 * onClick(View)}.
	 */
	public boolean isStuck() {
		return mIsStuck;
	}

	/**
	 * Makes this {@link Button}'s {@link View.OnClickListener OnClickListener} react to the
	 * next click.
	 */
	public void unstick() {
		mLock.lock();
		try {
			if (mIsStuck) {
				mLockInEffect.signal();
				mIsStuck = false;
				setEnabled(true);
			}
		} finally {
			mLock.unlock();
		}
	}
	
	/**
	 * Prevents this {@link Button}'s {@link View.OnClickListener OnClickListener} from calling
	 * {@link View.OnClickListener#onClick(View) onClick(View)} upon any subsequent clicks until
	 * {@link #unstick()} is called. This method will also allow you to determine whether or not
	 * the {@code OnClickListener} should be called on the initial click.
	 * @param callListener if {@code true}, {@link #mOnClickListener} will call {@code onClick(View)}
	 * Setting this to {@code false} can be useful if you need to restore the {@link StickyButton}'s
	 * state (e.g., after a screen rotation).
	 */
	private void click(boolean callListener) {
		mLock.lock();
		try {
			while (mIsStuck) {
				try {
					mLockInEffect.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			mIsStuck = true;
			setEnabled(false);
			if (callListener && mOnClickListener != null) {
				mOnClickListener.onClick(this);
			}
		} finally {
			mLock.unlock();
		}
	}
	
	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		SavedState ss = new SavedState(superState);
		ss.isStuck = isStuck();
		return ss;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (!(state instanceof SavedState)) {
			super.onRestoreInstanceState(state);
		}
		
		SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		if (ss.isStuck) {
			click(false);
		}
	}

	protected static class SavedState extends BaseSavedState {
		boolean isStuck;

		SavedState(Parcelable superState) {
			super(superState);
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(isStuck ? 1 : 0);
		}

		public static final Parcelable.Creator<SavedState> CREATOR
		= new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
		
		private SavedState(Parcel in) {
			super(in);
			isStuck = (in.readInt() != 0);
		}
	}

}
