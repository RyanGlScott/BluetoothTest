package edu.kufpg.bluetooth.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.app.Application;

/**
 * This class manages the connection between an application's {@link Activity Activities}
 * and the {@link AsyncActivityTask AsyncActivityTasks} that they spawn. The difference
 * between this class and {@link BaseApplication} is that this class does not require Guava
 * as a dependency, which eliminates the need for a 2 MB library (at the cost of slightly
 * more overhead).
 * @param <A> The {@code Activity} class being managed.
 */
public class NoGuavaBaseApplication<A extends Activity> extends Application {
	/**
	 * An {@link Activity} can spawn any number of {@link android.os.AsyncTask AsyncTasks}
	 * simultaneously, so use a {@link Map} to connect an {@code Activity}'s name and
	 * its {@link AsyncActivityTask AsyncActivityTasks}.
	 */
	private Map<String, List<AsyncActivityTask<A,?,?,?>>> mActivityTaskMap =
			new HashMap<String, List<AsyncActivityTask<A,?,?,?>>>();

	/**
	 * Removes unused {@link AsyncActivityTask AsyncActivityTasks} after they have
	 * completed execution.
	 * @param activity The {@link Activity} that spawned the {@code AsyncActivityTask}.
	 * @param task The {@code AsyncActivityTask} to remove.
	 */
	public void removeTask(A activity, AsyncActivityTask<A,?,?,?> task) {
		String key = activity.getClass().getCanonicalName();
		List<AsyncActivityTask<A,?,?,?>> tasks = mActivityTaskMap.get(key);
		tasks.remove(activity);
		if (tasks.size() == 0) {
			mActivityTaskMap.remove(key);
		}
	}

	/**
	 * Establishes a connection between an {@link Activity} and a {@link AsyncActivityTask}
	 * that will persist through device rotation or standby.
	 * @param activity The {@code Activity} that spawned the {@code AsyncActivityTask}.
	 * @param task The {@code AsyncActivityTask} to connect.
	 */
	public void addTask(A activity, AsyncActivityTask<A,?,?,?> task) {
		String key = activity.getClass().getCanonicalName();
		List<AsyncActivityTask<A,?,?,?>> tasks = mActivityTaskMap.get(key);
		if (tasks == null) {
			tasks = new ArrayList<AsyncActivityTask<A,?,?,?>>();
			mActivityTaskMap.put(key, tasks);
		}

		tasks.add(task);
	}

	/**
	 * While an {@link Activity} rotates or is in standby, attempting to call an {@code Activity}
	 * method from one of its {@link AsyncActivityTask AsyncActivityTasks} can produce
	 * unexpected results. Use this method to set all of an {@code Activity}'s references
	 * in its tasks to {@code null} so that the tasks can work around rotation or standby.
	 * @param activity The {@code Activity} whose references should be set to null.
	 */
	public void detachActivity(A activity) {
		List<AsyncActivityTask<A,?,?,?>> tasks = mActivityTaskMap.get(activity.getClass().getCanonicalName());
		if (tasks != null) {
			for (AsyncActivityTask<A,?,?,?> task : tasks) {
				task.setActivity(null);
			}
		}
	}

	/**
	 * Reestablishes the connection between an {@link Activity} and its {@link AsyncActivityTask
	 * AsyncActivityTasks} after the {@code Activity} is resumed.
	 * @param activity The {@code Activity} whose references should be reestablished.
	 */
	public void attachActivity(A activity) {
		List<AsyncActivityTask<A,?,?,?>> tasks = mActivityTaskMap.get(activity.getClass().getCanonicalName());
		if (tasks != null) {
			for (AsyncActivityTask<A,?,?,?> task : tasks) {
				task.setActivity(activity);
			}
		}
	}
}