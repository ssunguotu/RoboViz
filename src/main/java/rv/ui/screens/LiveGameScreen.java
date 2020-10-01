/*
 *  Copyright 2011 RoboViz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rv.ui.screens;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import jsgl.math.vector.Vec3f;
import org.magmaoffenburg.roboviz.gui.MainWindow;
import org.magmaoffenburg.roboviz.configuration.Config.Networking;
import org.magmaoffenburg.roboviz.configuration.Config.OverlayVisibility;
import org.magmaoffenburg.roboviz.rendering.CameraController;
import org.magmaoffenburg.roboviz.rendering.Renderer;
import rv.comm.rcssserver.GameState;
import rv.comm.rcssserver.ServerComm;
import rv.comm.rcssserver.ServerSpeedBenchmarker;
import rv.world.ISelectable;
import rv.world.Team;
import rv.world.WorldModel;
import rv.world.objects.Agent;
import rv.world.objects.Ball;

public class LiveGameScreen extends ViewerScreenBase implements ServerComm.ServerChangeListener
{
	private final PlaymodeOverlay playmodeOverlay;
	private final InfoOverlay connectionOverlay;

	public LiveGameScreen()
	{
		super();
		ServerSpeedBenchmarker ssb = new ServerSpeedBenchmarker();
		Renderer.world.getGameState().addListener(ssb);
		Renderer.netManager.getServer().addChangeListener(this);
		Renderer.netManager.getServer().addChangeListener(ssb);
		gameStateOverlay.addServerSpeedBenchmarker(ssb);
		playmodeOverlay = new PlaymodeOverlay(this);
		overlays.add(playmodeOverlay);
		connectionOverlay = new InfoOverlay().setMessage(getConnectionMessage());
		overlays.add(connectionOverlay);
	}

	@Override
	protected void loadOverlayVisibilities()
	{
		super.loadOverlayVisibilities();
		gameStateOverlay.setShowServerSpeed(OverlayVisibility.INSTANCE.getServerSpeed());
	}

	private ServerComm getServer()
	{
		return Renderer.netManager.getServer();
	}

	private void kickOff(boolean left)
	{
		resetTimeIfExpired();
		getServer().kickOff(left);
	}

	private void directFreeKick(boolean left)
	{
		resetTimeIfExpired();
		getServer().directFreeKick(left);
	}

	private void freeKick(boolean left)
	{
		resetTimeIfExpired();
		getServer().freeKick(left);
	}

	private void connect()
	{
		if (!getServer().isConnected())
			getServer().connect();
	}

	private void dropBall()
	{
		resetTimeIfExpired();
		getServer().dropBall();
	}

	private String getConnectionMessage()
	{
		String server = getServer().getServerHost() + ":" + getServer().getServerPort();
		GameState gameState = Renderer.world.getGameState();
		// in competitions, the server is restarted for the second half
		// display a viewer-friendly message in that case to let them know why the game has
		// "stopped"
		if (gameState.isInitialized() && Math.abs(gameState.getTime() - gameState.getHalfTime()) < 0.1)
			return "Waiting for second half...";
		else if (Networking.INSTANCE.getAutoConnect())
			return "Trying to connect to " + server + "...";
		else
			return "Press C to connect to " + server + ".";
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		super.keyPressed(e);

		switch (e.getKeyCode()) {
		case KeyEvent.VK_X:
			if (e.isShiftDown())
				getServer().killServer();
			break;
		case KeyEvent.VK_K:
			kickOff(true);
			break;
		case KeyEvent.VK_J:
			kickOff(false);
			break;
		case KeyEvent.VK_O:
			if (Renderer.world.getGameState() != null &&
					Renderer.world.getGameState().getPlayModes() != null) {
				openPlaymodeOverlay();
			}
			break;
		case KeyEvent.VK_C:
			if (!e.isControlDown())
				connect();
			break;
		case KeyEvent.VK_L:
			if (e.isShiftDown())
				directFreeKick(true);
			else
				freeKick(true);
			break;
		case KeyEvent.VK_R:
			if (e.isShiftDown())
				directFreeKick(false);
			else
				freeKick(false);
			break;
		case KeyEvent.VK_T:
			if (e.isShiftDown())
				getServer().resetTime();
			break;
		case KeyEvent.VK_U:
			getServer().requestFullState();
			break;
		case KeyEvent.VK_B:
			dropBall();
			break;
		case KeyEvent.VK_M:
			toggleShowServerSpeed();
			break;
		}
	}

	private void openPlaymodeOverlay()
	{
		setEnabled(MainWindow.glCanvas, false);
		playmodeOverlay.setVisible(true);
	}

	private void resetTimeIfExpired()
	{
		// changing the play mode doesn't have any effect if the game has ended
		float gameTime = Renderer.world.getGameState().getHalfTime() * 2;
		if (Renderer.world.getGameState().getTime() >= gameTime)
			Renderer.netManager.getServer().resetTime();
	}

	@Override
	public void mouseClicked(MouseEvent e)
	{
		if (Renderer.netManager.getServer().isConnected()) {
			super.mouseClicked(e);
		}
	}

	@Override
	protected boolean selectedObjectClick(ISelectable object, MouseEvent e)
	{
		if (e.isControlDown()) {
			Vec3f fieldPos = CameraController.objectPicker.pickField();
			moveSelection(fieldPos);
			return true;
		}
		return false;
	}

	@Override
	protected void altClick(MouseEvent e)
	{
		Vec3f fieldPos = CameraController.objectPicker.pickField();
		if (e.isControlDown()) {
			pushBallTowardPosition(fieldPos, false);
		} else if (e.isShiftDown()) {
			pushBallTowardPosition(fieldPos, true);
		}
	}

	private void moveSelection(Vec3f pos)
	{
		if (pos == null)
			return;
		ISelectable selected = Renderer.world.getSelectedObject();
		pos.y = selected.getBoundingBox().getCenter().y + 0.1f;
		Vec3f serverPos = WorldModel.COORD_TFN.transform(pos);

		if (selected instanceof Ball) {
			serverPos.z = Renderer.world.getGameState().getBallRadius();
			Renderer.netManager.getServer().moveBall(serverPos);
		} else if (selected instanceof Agent) {
			Agent a = (Agent) selected;
			boolean leftTeam = a.getTeam().getID() == Team.LEFT;
			Renderer.netManager.getServer().moveAgent(serverPos, leftTeam, a.getID());
		}
	}

	private void pushBallTowardPosition(Vec3f pos, boolean fAir)
	{
		if (pos == null)
			return;

		Vec3f targetPos = WorldModel.COORD_TFN.transform(pos);
		Vec3f ballPos = WorldModel.COORD_TFN.transform(Renderer.world.getBall().getPosition());
		ballPos.z = Renderer.world.getGameState().getBallRadius();
		Vec3f vel;
		float xDiff = targetPos.x - ballPos.x;
		float yDiff = targetPos.y - ballPos.y;
		float xyDist = (float) Math.sqrt(xDiff * xDiff + yDiff * yDiff);
		if (fAir) {
			final float AIR_XY_POWER_FACTOR =
					(float) Math.sqrt(9.81 * xyDist * (.82 + .022 * xyDist)); // with no drag =
																			  // (float)Math.sqrt(9.81*xyDist/2);
			final float Z_POWER = AIR_XY_POWER_FACTOR;
			vel = new Vec3f((float) Math.cos(Math.atan2(yDiff, xDiff)) * AIR_XY_POWER_FACTOR,
					(float) Math.sin(Math.atan2(yDiff, xDiff)) * AIR_XY_POWER_FACTOR, Z_POWER);
		} else {
			final float GROUND_XY_POWER_FACTOR = 1.475f;
			vel = new Vec3f(xDiff * GROUND_XY_POWER_FACTOR, yDiff * GROUND_XY_POWER_FACTOR, 0.0f);
		}
		Renderer.netManager.getServer().moveBall(ballPos, vel);
	}

	@Override
	public void connectionChanged(ServerComm server)
	{
		connectionOverlay.setMessage(getConnectionMessage());
		connectionOverlay.setVisible(!server.isConnected());
		if (server.isConnected()) {
			Renderer.world.setSelectedObject(Renderer.world.getBall());
		} else {
			playmodeOverlay.setVisible(false);
			prevScoreL = -1;
			prevScoreR = -1;
		}
	}

	@Override
	public void gsPlayStateChanged(GameState gs)
	{
		super.gsPlayStateChanged(gs);

		if (gs.hasPlayModeJustChanged()) {
			switch (gs.getPlayMode()) {
			case GameState.KICK_OFF_LEFT:
			case GameState.KICK_OFF_RIGHT:
				getServer().requestFullState();
				break;
			}

			if (gs.getPlayMode().equals(GameState.GAME_OVER)) {
				connectionOverlay.setMessage(String.format("Half Over, %s %d:%d %s", gs.getUIStringTeamLeft(),
						gs.getScoreLeft(), gs.getScoreRight(), gs.getUIStringTeamRight()));
				connectionOverlay.setVisible(true);
			} else {
				connectionOverlay.setVisible(false);
			}
		}
	}
}