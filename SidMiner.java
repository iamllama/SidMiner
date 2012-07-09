import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.powerbot.concurrent.Task;
import org.powerbot.concurrent.strategy.Strategy;
import org.powerbot.game.api.ActiveScript;
import org.powerbot.game.api.Manifest;
import org.powerbot.game.api.methods.Calculations;
import org.powerbot.game.api.methods.Environment;
import org.powerbot.game.api.methods.Game;
import org.powerbot.game.api.methods.Tabs;
import org.powerbot.game.api.methods.Walking;
import org.powerbot.game.api.methods.Widgets;
import org.powerbot.game.api.methods.input.Mouse;
import org.powerbot.game.api.methods.interactive.Players;
import org.powerbot.game.api.methods.node.Menu;
import org.powerbot.game.api.methods.node.SceneEntities;
import org.powerbot.game.api.methods.tab.Inventory;
import org.powerbot.game.api.methods.tab.Skills;
import org.powerbot.game.api.methods.widget.Camera;
import org.powerbot.game.api.util.Filter;
import org.powerbot.game.api.util.Random;
import org.powerbot.game.api.util.Time;
import org.powerbot.game.api.util.Timer;
import org.powerbot.game.api.wrappers.Tile;
import org.powerbot.game.api.wrappers.interactive.Player;
import org.powerbot.game.api.wrappers.node.SceneObject;
import org.powerbot.game.api.wrappers.widget.WidgetChild;
import org.powerbot.game.bot.event.MessageEvent;
import org.powerbot.game.bot.event.listener.MessageListener;
import org.powerbot.game.bot.event.listener.PaintListener;

@Manifest(authors = { "siidheesh" }, name = "SidMiner", version = 2.0, description = "Advanced RSBot Miner", topic = 4, website = "http://is.gd/qoAkdq", vip = true)
public class SidMiner extends ActiveScript implements PaintListener, MouseListener, MessageListener {
	
	public boolean no_ore_in_sight = false;
	public boolean stop_all = false;

	public void error(final String s) {
		Toolkit.getDefaultToolkit().beep();
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
                try {
                	JOptionPane.showMessageDialog(null, s, "Error!", JOptionPane.WARNING_MESSAGE);
                } catch (Exception e) {
                	e.printStackTrace();
                }
            }
        });
		Game.logout(true);
		SidMiner.this.stop();
	}
	
	private class Drop extends Strategy implements Task {

		boolean HOP = true;

		private void clickItem(final WidgetChild item) {
			if (!item.getBoundingRectangle().contains(Mouse.getLocation())) {
				move(item.getCentralPoint().x, item.getAbsoluteY() + 5, HOP);
				Time.sleep(20, 50);
			}
			Mouse.click(false);
			Time.sleep(20, 50);
			move(Mouse.getX(), getDropActionYLocation(), HOP);
			Time.sleep(20, 50);
			Mouse.click(true);
			Time.sleep(20, 50);
		}

		private int getDropActionYLocation() {
			final String[] actions = Menu.getItems();
			for (int i = 0; i < actions.length; i++) {
				if (actions[i].contains("Drop ")) {
					return Menu.getLocation().y + 21 + 16 * i + Random.nextInt(3, 6);
				}
			}
			return Menu.getLocation().y + 40;
		}

		private boolean itemIsDroppable(int itemID) {
			for (int i = 0; i < ItemsToNotDrop.length; ++i) {
				int check = ItemsToNotDrop[i];
				if (itemID == check) {
					return false;
				}
			}
			return true;
		}

		private void move(final int x, final int y, final boolean Hop) {
			if (Hop) {
				Mouse.hop(x, y, 3, 3);
			} else {
				Mouse.move(x, y, 3, 3);
			}
		}

		@Override
		public void run() {
			status = "Dropping items.";
			log.info(status);
			final WidgetChild inv = Widgets.get(679, 0);
			for (int x = 0; x < 4; x++) {
				for (int y = x; y < 28; y += 4) {
					final WidgetChild spot = inv.getChild(y);
					if (spot != null && itemIsDroppable(spot.getChildId())) {
						clickItem(spot);
						Time.sleep(20, 50);
					}
				}
			}
			log.info("Items dropped.");
		}

		@Override
		public boolean validate() {
			return Inventory.isFull() && Players.getLocal().getAnimation() == -1 && Widgets.get(679).validate() == true && !stop_all;
		}
	}
	private class Mining extends Strategy implements Task {
		int timeout = 0;
		int sweepcheck = 0;
		boolean extrange = false;
		
		@Override
		public void run() {
			if (!Tabs.INVENTORY.isOpen()) {
				Tabs.INVENTORY.open();
			}
			status = "Looking for " + rockname + "...";
			final SceneObject Ore = SceneEntities.getNearest(new Filter<SceneObject>() {
				@Override
				public boolean accept(SceneObject SceneObject) {
					for (int i : rock) {
						if (SceneObject.getId() == i) { //first check if there are rocks we want
							if (Calculations.distanceTo(SceneObject) < oreCheckDistance) { //then check if they are near
								if (extrange) oreCheckDistance = 5;
								return true;
							}
						}
					}
					timeout++;
					return false;
				}
			});
			if (Ore != null) {
				if (Ore.isOnScreen()) {
					Tile targetedOreLoc = Ore.getLocation();
					int targetedOreID = SceneEntities.getAt(targetedOreLoc).getId(), miningWaitPatience = 0;
					if (Ore.interact("Mine")) {
						sweepcheck = 0;
						no_ore_in_sight = false;
						status = "Mining " + rockname + "...";
						while (targetedOreID == SceneEntities.getAt(targetedOreLoc).getId() && miningWaitPatience < 80) {
							Time.sleep(50, 100);
							miningWaitPatience++;
						}
					}
				}
			} else { //try find for ore further
				if (sweepcheck > 4 && sweepcheck <= 24) {
					status = "Scanning area";
					log.info("scanning area:" + (sweepcheck - 4));
					Camera.setAngle(Random.nextInt(-359, 359));
					Camera.setPitch(Random.nextInt(1, 99));
					Camera.setPitch(Random.nextInt(1, 50));
				} else if (sweepcheck > 24 && sweepcheck <= 50) { //truly no ore within here!!
					no_ore_in_sight = true;
					log.info("It's hopeless.... No ore in sight....");
					sweepcheck++;
				} else if (sweepcheck > 50) {
					log.info("timeout");
					error("There's no ore to mine!!!");
				}
				if (timeout > 10 && oreCheckDistance < 40 && !no_ore_in_sight) { //increase range, max 40
					status = "Extending search range..";
					oreCheckDistance = (int) Math.round(1.35*oreCheckDistance);
					extrange = true;
					timeout = 0;
					sweepcheck++;
					log.info("oreCheckDistance:" + oreCheckDistance);
				} else status = "Waiting for " + rockname + " ore...";
			}
		}

		@Override
		public boolean validate() {
			if (!Inventory.isFull() && !stop_all) {
				return true;
			}
			return false;
		}
	}
	final class WarMinerGui extends JFrame implements ActionListener {

		private static final long serialVersionUID = 1L;
		boolean startup = true;
		
		public WarMinerGui() {
			setTitle("SidMiner V 1.5");
			setAlwaysOnTop(true);
			final JButton start = new JButton("Start");
			start.setToolTipText("Start powermining selected ore.");
			start.setBounds(10, 40, 60, 20);
			start.addActionListener(this);
			getContentPane().setLayout(null);
			getContentPane().add(start);
			final JComboBox Rock = new JComboBox();
			Rock.setModel(new DefaultComboBoxModel(new String[] { "Clay", "Copper", "Tin", "Iron", "Coal", "Silver", "Gold", "Mithril", "Adamantite", "Rune", "Idle" }));
			Rock.setAlignmentX(Component.LEFT_ALIGNMENT);
			Rock.setBounds(10, 10, 85, 20);
			// Ore defaults to iron, 'cause everyone loves iron.
			Rock.setSelectedIndex(3);
			Rock.setToolTipText("Choose an ore type to powermine.");
			getContentPane().add(Rock);
			setSize(110, 130);
			start.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String t = Rock.getSelectedItem().toString();
					rockname = t;
					if (t == "Tin") {
						rock = TinRockID;
						rockXP = 17.5;

					} else if (t == "Copper") {
						rock = CopperRockID;
						rockXP = 17.5;

					} else if (t == "Iron") {
						rock = IronRockID;
						rockXP = 35;

					} else if (t == "Silver") {
						rock = SilverRockID;
						rockXP = 40;

					} else if (t == "Gold") {
						rock = GoldRockID;
						rockXP = 65;

					} else if (t == "Coal") {
						rock = CoalRockID;
						rockXP = 50;

					} else if (t == "Clay") {
						rock = ClayID;
						rockXP = 5;

					} else if (t == "Mithril") {
						rock = MithrilRockID;
						rockXP = 80;

					} else if (t == "Adamantite") {
						rock = AdamantiteRockID;
						rockXP = 95;

					} else if (t == "Rune") {
						rock = RuniteRockID;
						rockXP = 125;

					} else if (t == "Idle") {
						rock = null;
						rockname = "nothing";
					}
					setVisible(false);
				}
			});
			Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
			int x = (int) ((dimension.getWidth() - getWidth()) / 2);
			int y = (int) ((dimension.getHeight() - getHeight()) / 2);
			setLocation(x, y);
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			this.setVisible(false);
			this.dispose();
			log.info("Ore Chosen:" + rockname + ".");
			if(startup) StartTime = System.currentTimeMillis();
		}
	}
	private class Antiban extends Strategy implements Task {
		@Override
		public boolean validate() {
			return Game.isLoggedIn() && Random.nextInt(0, 200) < 10;
		}
		@Override
		public void run() {
			status = "Antibanning...";
			final int rand = Random.nextInt(0, 12);
			log.info("antiban chosen: "+rand);
			switch (rand) {
				case 1:
					Camera.setPitch(Random.nextBoolean());
					break;
				case 2:
					Camera.setPitch(Random.nextInt(1, 100));
					break;
				case 3:
					Camera.setAngle(Random.nextInt(1, 360));
					break;
				case 4:
					final SceneObject[] objects = SceneEntities.getLoaded();
					final int random = Random.nextInt(0, objects.length - 1);
					if (objects[random] != null) {
						Camera.turnTo(objects[random], 3);
					}
					break;
				case 6:
					if(Walking.getEnergy() > Random.nextInt(60, 95) && !Walking.isRunEnabled())
					{
						Walking.setRun(true);
					}
					break;
				case 7:
				case 8:
				case 9:
				case 10:
					final Point p = Mouse.getLocation();
		            Mouse.move(new Point(Random.nextInt(-50, 50) + p.x, Random.nextInt(-50, 50) + p.y));
					break;
				case 11:
					final int offset = Random.nextInt(1, 300);
					Mouse.move(Mouse.getX() + Random.nextInt(-offset, offset),
					Mouse.getY() + Random.nextInt(-offset, offset));
					break;
				case 12:
					final Player[] players = Players.getLoaded();
					final int random2 = Random.nextInt(0, players.length - 1);
					if (players[random2] != null) {
						Camera.turnTo(players[random2], 3);
					}
					break;
			}
		}
	}
	
	public static void drawMouse(final Graphics g) {
		// Draw cursor shape
		//g.setColor(new Color((int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255)));
		Polygon cursorShape1 = new Polygon();
		Polygon cursorShape2 = new Polygon();
		cursorShape1.addPoint(Mouse.getX(), Mouse.getY() - 2);
		cursorShape1.addPoint(Mouse.getX() - 2, Mouse.getY() + 2);
		cursorShape1.addPoint(Mouse.getX() + 2, Mouse.getY() + 2);
		cursorShape2.addPoint(Mouse.getX(), Mouse.getY() - 6);
		cursorShape2.addPoint(Mouse.getX() - 6, Mouse.getY() + 4);
		cursorShape2.addPoint(Mouse.getX() + 6, Mouse.getY() + 4);
		g.drawPolygon(cursorShape1);
		//g.setColor(new Color((int) (Math.random() * 255), (int) (Math.random() * 255), (int) (Math.random() * 255)));
		g.drawPolygon(cursorShape2);

		// Draw cursor lines
		Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
		final Point location = Mouse.getLocation();
		final long mouseHoldTime = System.currentTimeMillis() - Mouse.getPressTime();
		if (Mouse.getPressTime() == -1 || mouseHoldTime >= 100) {
			g.setColor(new Color(0, 0, 0, 50));
			g.drawLine(0, location.y, (int) dimension.getWidth(), location.y);
			g.setColor(new Color(255, 255, 255, 50));
			g.drawLine(location.x, 0, location.x, (int) dimension.getHeight());
			g.setColor(new Color(255, 255, 255, 50));
			g.drawLine(0, location.y + 1, (int) dimension.getWidth(), location.y + 1);
			g.drawLine(0, location.y - 1, (int) dimension.getWidth(), location.y - 1);
			g.setColor(new Color(0, 0, 0, 50));
			g.drawLine(location.x + 1, 0, location.x + 1, (int) dimension.getHeight());
			g.drawLine(location.x - 1, 0, location.x - 1, (int) dimension.getHeight());
		}
		if (mouseHoldTime < 100) {
			g.setColor(new Color(0, 255, 0));
			g.drawLine(0, location.y, (int) dimension.getWidth(), location.y);
			g.drawLine(location.x, 0, location.x, (int) dimension.getHeight());
			g.setColor(new Color(0, 0, 0, 50));
			g.drawLine(0, location.y + 1, (int) dimension.getWidth(), location.y + 1);
			g.drawLine(0, location.y - 1, (int) dimension.getWidth(), location.y - 1);
			g.drawLine(location.x + 1, 0, location.x + 1, (int) dimension.getHeight());
			g.drawLine(location.x - 1, 0, location.x - 1, (int) dimension.getHeight());
		}
	}
	
	// Rock IDS
	public int CopperRockID[] = { 2090, 2091, 2097, 3027, 3229, 5779, 5780, 5781, 9708, 9709, 9710, 11936, 11937, 11938, 11960, 11961, 11962, 14906, 14907, 21284, 21285, 21286 };
	public int TinRockID[] = { 2094, 2095, 3038, 3245, 5776, 5777, 5778, 9714, 9716, 11933, 11934, 11935, 11957, 11958, 11959, 14902, 14903, 21293, 21294, 21295 };
	public int IronRockID[] = { 2092, 2093, 5773, 5774, 5775, 9717, 9718, 9719, 11954, 11955, 11956, 14099, 14107, 14913, 14914, 21281, 21282, 21283, 37307, 37308, 37309 };
	public int CoalRockID[] = { 2096, 2097, 3032, 3233, 5770, 5771, 5772, 10948, 11930, 11931, 11932, 14850, 14851, 21287, 21288, 21289, 29215, 29216, 29217, 32426, 32427, 32428 };
	public int ClayID[] = { 46320, 46322, 46324, 67006, 67007, 67008 };
	public int SilverRockID[] = { 2100, 2101, 2311, 11186, 11187, 11188, 11948, 11949, 11950, 15580, 15581, 29224, 29225, 29226, 32444, 32445, 32446, 37304, 37305, 37306 };
	public int GoldRockID[] = { 2098, 5768, 5769, 9720, 9722, 10574, 10575, 10576, 11183, 11184, 11185, 11943, 15576, 15577, 15578, 32432, 32433, 32434, 45067, 45068 };
	public int MithrilRockID[] = { 2102, 2103, 3041, 3280, 5784, 5785, 5786, 11942, 11943, 11944, 21278, 21279, 21280, 32438, 32439, 32440 };
	public int AdamantiteRockID[] = { 2104, 2105, 3040, 3273, 5782, 5783, 11939, 11941, 21275, 21276, 21277, 29233, 29235, 32425, 32436, 32437 };
	public int RuniteRockID[] = { 14859, 33078, 33079, 45069, 45070 };
	public int ItemsToNotDrop[] = { 14664, 2528, 314, 1265, 1267, 1269, 1273, 1271, 1275, 15259 };
	
	public Timer timer;
	public int[] rock;
	public String rockname = "nothing";
	public double rockXP;
	public int startlvl, startxp = -1;
	public int ores = 0;
	public int oreCheckDistance = 5;
	boolean showPaint = true;
	public long StartTime = -1;
	public String status = "";

	public final DecimalFormat df = new DecimalFormat("###,###,###,###");
	public final DecimalFormat pf = new DecimalFormat("###.###");

	int spinner = 0, wait = 0;

	/*
	private void CameraCheck() {
		if (Math.abs(Camera.getPitch() - cameraPitch) > 15) {
			Camera.setPitch(Random.nextInt(cameraPitch - 10, cameraPitch + 15));
		}
		if (Math.abs(Camera.getYaw() - cameraYaw) > 15) {
			Camera.setAngle(Random.nextInt(cameraYaw - 10, cameraYaw + 15));
		}
	}
	 */
	
	public double getPercentNextLevel(int targetLevel) {
		final int level = Skills.getLevel(14); //mining lvl
		if (level == 99) {
			return 100;
		}
		final double range = Skills.XP_TABLE[targetLevel] - Skills.XP_TABLE[level];
		final double currentLvlExp = Skills.getExperienceToLevel(14, targetLevel);
		return 1 - currentLvlExp / range;
	}

	// Message event to compliment ores mined:
	@Override
	public void messageReceived(MessageEvent e) {
		if (e.getMessage().toLowerCase().contains("manage to mine")) {
			ores++;
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {

	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mousePressed(MouseEvent e) {
		// Show/hide paint.
		if (showPaint) {
			if (e.getX() >= 496 && e.getX() < 516 && e.getY() >= 189 && e.getY() < 208) {
				if (!showPaint) {
					showPaint = true;
				} else {
					showPaint = false;
				}
			} else if (e.getX() >= 466 && e.getX() < 486 && e.getY() >= 189 && e.getY() < 208) {
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							final WarMinerGui GUI = new WarMinerGui();
							GUI.startup = false;
							GUI.setVisible(true);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				});
			}
		} else {
			if (e.getX() >= 496 && e.getX() < 516 && e.getY() >= 317 && e.getY() < 337) {
				if (!showPaint) {
					showPaint = true;
				} else {
					showPaint = false;
				}
			}
		}
		log.info("Mouse pressed at: " + e.getX() + "," + e.getY());
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onRepaint(Graphics render) {
		Graphics2D g2 = (Graphics2D) render;
		if (timer == null) {
			timer = new Timer(0);
		}

		final Point loc = new Point(4, 189);
		final Dimension size = new Dimension(512, 148);
		final int trigx[] = { 469, 483, 476 };
		final int trigy[] = { 194, 194, 204 };
		if (no_ore_in_sight) {
			g2.setColor(Color.RED);
            g2.setFont(new Font(null, Font.BOLD, 35));
            //y=loc.y + 126 - 53 + 12
            //x=loc.x + 178 - 176 + 195
            g2.drawString("NO VISIBLE ORE", loc.x + 206, loc.y + 85);
		}
		if (showPaint) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			// Fill for Main HUD frame background
			GradientPaint hudBackground = new GradientPaint(loc.x / 2, loc.y, new Color(200, 200, 200, 100), loc.x / 2, loc.y + size.height, new Color(119, 119, 255, 150));
			g2.setPaint(hudBackground);
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y, size.width, size.height, 5, 5));

			// Main HUD frame (whole middle box)
			render.setColor(new Color(255, 255, 255));
			render.drawRoundRect(loc.x, loc.y, size.width, size.height, 5, 5);
			render.drawLine(loc.x, loc.y + 20, loc.x + size.width, loc.y + 20);

			// HUD background for leveling/points statistics (middle left box with stats in it)
			render.drawRoundRect(loc.x, loc.y + 20, 178, 106, 5, 5);
			GradientPaint statsBackground = new GradientPaint(loc.x, loc.y, new Color(200, 200, 200, 100), loc.x, loc.y + 86, new Color(119, 255, 119, 50));
			g2.setPaint(statsBackground);
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 20, 178, 106, 5, 5));

			// Set current level in top bar.
			render.setColor(new Color(255, 255, 255));
			render.drawRoundRect(loc.x, loc.y, 120, 20, 5, 5);
			render.setFont(new Font("Tahoma", 0, 12));
			render.drawString("Miner LVL: " + Skills.getLevel(Skills.MINING) + " (+" + (Skills.getLevel(Skills.MINING) - startlvl) + ")", loc.x + 5, loc.y + 15);

			// Display status in top bar.
			render.drawString(status, loc.x + 125, loc.y + 15);

			// Statistics section:

			// Draw elapsed time
			render.setFont(new Font("Tahoma", 0, 10));
			render.drawString("Time: ", loc.x + 5, loc.y + 41);
			if (StartTime > 0) {
				render.drawString(Time.format(System.currentTimeMillis() - StartTime), loc.x + 105, loc.y + 41);
			} else {
				render.drawString("00:00:00", loc.x + 105, loc.y + 41);
			}

			// Display total xp gained during script run
			render.drawString("Exp Gained", loc.x + 5, loc.y + 51);
			int xpGained = Skills.getExperience(Skills.MINING) - startxp;
			render.drawString(df.format(xpGained), loc.x + 105, loc.y + 51);

			// Display xp gained per hour
			render.drawString("Exp/hour:", loc.x + 5, loc.y + 61);
			long XPPerHour = (long) ((Skills.getExperience(Skills.MINING) - startxp) * (3600000.0 / (System.currentTimeMillis() - StartTime)));
			render.drawString(df.format(XPPerHour), loc.x + 105, loc.y + 61);

			// Display xp until next level
			render.drawString("XP to " + (Skills.getLevel(Skills.MINING) + 1) + ":", loc.x + 5, loc.y + 71);
			int xpToNextLevel = Skills.getExperienceToLevel(Skills.MINING, Skills.getLevel(Skills.MINING) + 1);
			render.drawString(df.format(xpToNextLevel), loc.x + 105, loc.y + 71);

			// Display Time until next level
			render.drawString("Time to " + (Skills.getLevel(Skills.MINING) + 1), loc.x + 5, loc.y + 81);
			try {
				if (XPPerHour > 0) {
					double xpPerMs = xpGained * (1.0 / (System.currentTimeMillis() - StartTime));
					int ms = (int) (xpToNextLevel / xpPerMs);
					int secs = ms / 1000 % 60;
					int mins = ms / (1000 * 60) % 60;
					int hrs = ms / (1000 * 60 * 60) % 24;
					int days = ms / (1000 * 60 * 60 * 24);
					String TTL = String.format("%02d:%02d:%02d:%02d", days, hrs, mins, secs);
					render.drawString(TTL, loc.x + 105, loc.y + 81);
				} else {
					render.drawString("--:--:--:--", loc.x + 105, loc.y + 81);
				}
			} catch (Exception e) {
				render.drawString("-1:-1:-1:-1", loc.x + 105, loc.y + 81);
			}

			// Display xp needed to reach level 99
			render.drawString("XP to 99:", loc.x + 5, loc.y + 91);
			int xpTo99 = Skills.getExperienceToLevel(Skills.MINING, 99);
			render.drawString(df.format(xpTo99), loc.x + 105, loc.y + 91);

			// Display Time until level 99
			render.drawString("Time to 99", loc.x + 5, loc.y + 101);
			try {
				if (XPPerHour > 0) {
					double xpPerMs = xpGained * (1.0 / (System.currentTimeMillis() - StartTime));
					int msto99 = (int) (xpTo99 / xpPerMs);
					int secsto99 = msto99 / 1000 % 60;
					int minsto99 = msto99 / (1000 * 60) % 60;
					int hrsto99 = msto99 / (1000 * 60 * 60) % 24;
					int daysto99 = msto99 / (1000 * 60 * 60 * 24);
					String TT99 = String.format("%02d:%02d:%02d:%02d", daysto99, hrsto99, minsto99, secsto99);
					render.drawString(TT99, loc.x + 105, loc.y + 101);
				} else {
					render.drawString("--:--:--:--", loc.x + 105, loc.y + 101);
				}
			} catch (Exception e) {
				render.drawString("-1:-1:-1:-1", loc.x + 105, loc.y + 101);
			}

			// Display ores mined
			render.drawString("Ores mined: ", loc.x + 5, loc.y + 111);
			render.drawString(df.format(ores), loc.x + 105, loc.y + 111);
			int oresPerHour = (int) (ores * (3600000.0 / (System.currentTimeMillis() - StartTime)));
			render.drawString("Ores/hour:", loc.x + 5, loc.y + 121);
			render.drawString(df.format(oresPerHour), loc.x + 105, loc.y + 121);

			// Use AaiMister's XP bar idea:
			// Draw Next level percent bar:
			g2.setColor(new Color(163, 4, 0));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 126, size.width + 1, size.height - 137, 5, 5));
			g2.setColor(new Color(0, 163, 4));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 126, getPercentNextLevel(Skills.getLevel(Skills.MINING) + 1) * size.width + 1, size.height - 137, 5, 5));
			g2.setColor(new Color(255, 255, 255, 90));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 126, size.width, size.height - 143, 5, 5));
			g2.setColor(new Color(255, 255, 255));
			g2.drawRoundRect(loc.x, loc.y + 126, size.width, size.height - 137, 5, 5);
			render.drawString(pf.format(100 * getPercentNextLevel(Skills.getLevel(Skills.MINING) + 1)) + "% to " + (Skills.getLevel(Skills.MINING) + 1), loc.x + size.width / 2, loc.y + size.height - 13);
			render.drawString("Total XP:", loc.x + 5, loc.y + size.height - 13);
			render.drawString(df.format(xpToNextLevel / rockXP) + " ores to " + (Skills.getLevel(Skills.MINING) + 1), (int) (loc.x + size.width * .75), loc.y + size.height - 13);
			// Draw 99 percent bar:
			g2.setColor(new Color(163, 4, 0));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 137, size.width + 1, size.height - 137, 5, 5));
			g2.setColor(new Color(4, 100, 163));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 137, Skills.getExperience(14) / 13034431.0 * size.width + 1, size.height - 137, 5, 5));
			g2.setColor(new Color(255, 255, 255, 90));
			g2.fill(new RoundRectangle2D.Double(loc.x, loc.y + 137, size.width, size.height - 143, 5, 5));
			g2.setColor(new Color(255, 255, 255));
			g2.drawRoundRect(loc.x, loc.y + 137, size.width, size.height - 137, 5, 5);
			render.drawString(pf.format(100 * (Skills.getExperience(14) / 13034431.0)) + "% to 99 ", loc.x + size.width / 2, loc.y + size.height - 2);
			render.drawString(df.format(Skills.getExperience(Skills.MINING)), loc.x + 5, loc.y + size.height - 2);
			render.drawString(df.format(xpTo99 / rockXP) + " ores to 99", (int) (loc.x + size.width * .75), loc.y + size.height - 2);

			// Open-Close HUD Area (When Open)
			g2.setColor(new Color(50, 0, 50));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 19, loc.y, 20, 20, 5, 5));
			g2.setColor(new Color(255, 255, 255, 80));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 19, loc.y, 20, 8, 5, 5));
			g2.setColor(new Color(255, 255, 255));
			g2.drawLine(loc.x + size.width - 20, loc.y, loc.x + size.width, loc.y + 20);
			g2.drawLine(loc.x + size.width - 1, loc.y + 1, loc.x + size.width - 19, loc.y + 19);
			g2.drawRoundRect(loc.x + size.width - 20, loc.y, 20, 20, 5, 5);

			//ore reselect button
			g2.setColor(new Color(50, 0, 50));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 49, loc.y, 20, 20, 5, 5));
			g2.setColor(new Color(255, 255, 255, 80));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 49, loc.y, 20, 8, 5, 5));
			g2.setColor(new Color(255, 255, 255));
			g2.fillPolygon(trigx, trigy, 3);
			g2.drawRoundRect(loc.x + size.width - 50, loc.y, 20, 20, 5, 5);
		} else if (!showPaint) {
			// Open-Close HUD Area (When Closed)
			g2.setColor(new Color(0, 50, 50));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 19, loc.y + size.height - 20, 20, 20, 5, 5));
			g2.setColor(new Color(255, 255, 255, 80));
			g2.fill(new RoundRectangle2D.Double(loc.x + size.width - 19, loc.y + size.height - 20, 20, 8, 2, 2));
			g2.setColor(new Color(255, 255, 255));
			g2.drawOval(loc.x + size.width - 19 + 4, loc.y + size.height - 20 + 5, 10, 10);
			g2.drawRoundRect(loc.x + size.width - 20, loc.y + size.height - 20, 20, 20, 5, 5);
		}

		// Draw the mouse overlay.
		drawMouse(render);
	}

	@Override
	protected void setup() {
		// On start-up
		log.info("Initializing.");
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					final WarMinerGui GUI = new WarMinerGui();
					GUI.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		startlvl = Skills.getLevel(Skills.MINING);
		startxp = Skills.getExperience(Skills.MINING);
		showPaint = true;

		Drop d1 = new Drop();
		Strategy drop = new Strategy(d1, d1);
		Mining m1 = new Mining();
		Strategy mining = new Strategy(m1, m1);
		Antiban ab = new Antiban();
		Strategy antiban = new Strategy(ab, ab);
		RemoteControl rc = new RemoteControl();
		Strategy remotecontrol = new Strategy(rc, rc);
		drop.setSync(true);
		antiban.setSync(true);
		remotecontrol.setSync(true);
		provide(drop);
		provide(mining);
		provide(antiban);
		provide(remotecontrol);
		log.info("Initialized. Choose your powermining target.");
	}
	
	private class RemoteControl extends Strategy implements Task {
		public int cmdbuf;
		public int reply;
		public boolean success;
		@Override
		public boolean validate() {
			//do polling here...
			return false;
		}
		@Override
		public void run() {
			//Interpret commands here...
			switch(cmdbuf) {
				case 1: //stop
					stop_all = true;
					success = true;
					break;
				case 2: //start
					stop_all = false;
					success = true;
					break;
				case 3: //logout
					while (Game.isLoggedIn()) {
			            Game.logout(true);
			            success = true;
			        }
					break;
				case 4: //screenshot	
					try {
						BufferedImage bi = Environment.captureScreen();
						File outputfile = new File("pic" + System.currentTimeMillis() + ".png");
						ImageIO.write(bi, "png", outputfile);
						success = true;
					} catch (Exception e) {
						success = false;
						e.printStackTrace();
					}
					break;
				default:
					break;
			}
			//reply with success var
		}
	}
}