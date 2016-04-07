package bots;

import java.util.ArrayList;
import java.util.List;

import pirates.game.Location;
import pirates.game.Powerup;
import pirates.game.Treasure;
import pirates.game.Pirate;
import pirates.game.PirateBot;
import pirates.game.PirateGame;

public class MyBot implements PirateBot {

	private List<Location> takenLocations;
	private List<Pirate> pirates;
	private int movesBank;
	private List<Location> occupiedTargets;
	private List<Pirate> attackedEnemies;

	@Override
	public void doTurn(PirateGame game) {
		movesBank = game.getActionsPerTurn();

		List<Pirate> alreadyshot = new ArrayList<Pirate>();
		List<Pirate> myPiratesWithTreasures = game.myPiratesWithTreasures();
		List<Pirate> myPiratesWithoutTreasures = game.myPiratesWithoutTreasures();

		for (Pirate p : myPiratesWithoutTreasures)
			if (p.getReloadTurns() != 0)
				alreadyshot.add(p);

		pirates = new ArrayList<>(myPiratesWithTreasures);
		pirates.addAll(myPiratesWithoutTreasures);

		takenLocations = new ArrayList<Location>();
		occupiedTargets = new ArrayList<Location>();
		attackedEnemies = new ArrayList<>();

		game.debug("Number of pirates is " + game.myPirates().size());

		if (game.myPirates().size() == 1) {
			gameWithOnePirate(game);
		} else if (game.myPirates().size() == 2 && game.enemyPirates().size() == 2) {
			gameWithTwoPirates(game);
		} else {  // The general case (N pirates, no special treatment)
			Pirate attacker = null;
			while (!pirates.isEmpty()) {
				boolean goneAway = false;
				int move = 0;
				Pirate pirate1 = pirates.remove(0);
				game.debug("processing pirate " + pirate1.getId());
				Location location = null;
				Pirate closestEnemy = findClosestShip(game, pirate1, game.enemySoberPirates());
				Pirate closestEnemyWithTreasure = findClosestShip(game, pirate1, game.enemyPiratesWithTreasures());
				Pirate enemyToAttack = findEnemyToAttack(pirate1, game);

				// Pirate enemyPirate = enemyWithPowerup(game);

				if (pirate1.getTurnsToSober() > 0 || pirate1.isLost()) {
					game.debug("Pirate " + pirate1.getId() + " is drunk or lost");
					continue;
				}
				if (enemyToAttack != null) {
					game.debug("Pirate " + pirate1.getId() + " is attacking enemy " + enemyToAttack.getId());
					game.attack(pirate1, enemyToAttack);
					attackedEnemies.add(enemyToAttack);
				} else if (shouldDefend(pirate1, game)) {
					game.defend(pirate1);
					game.debug("Pirate " + pirate1.getId() + " is defending from enemy " + closestEnemy.getId());
				} else {
					if (pirate1.hasTreasure()) {
						game.debug("Pirate " + pirate1.getId() + " has treasure and is going home");
						location = pirate1.getInitialLocation();
						move = pirate1.getCarryTreasureSpeed();
					} else if (closestEnemy != null && pirate1.getReloadTurns() == 0
							&& (attacker == null || game.treasures().isEmpty())) {
						attacker = pirate1;
						location = closestEnemy.getLocation();
						game.debug(
								"Pirate " + pirate1.getId() + " is going to attack enemy " + closestEnemy.getId());
						move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
					} else if (!alreadyshot.isEmpty() && pirate1.getReloadTurns() != 0
							&& closestEnemyWithTreasure != null && !pirate1.hasTreasure()) {
						location = enemyNextLocation(game, closestEnemyWithTreasure,
								closestEnemyWithTreasure.getInitialLocation());
						move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
						goneAway = true;
						game.debug("Pirate " + pirate1.getId() + " is going to sink enemy with treasure"
								+ closestEnemyWithTreasure.getId());
					}

					else {
						Treasure bestTreasure = findBestTreasure(game, pirate1);

						if (bestTreasure != null) {
							location = bestTreasure.getLocation();
							game.debug("Pirate " + pirate1.getId() + " is going to get treasure");
							move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
						}
					}
				}

				movesBank -= move;
				if (movesBank > 0 && pirates.isEmpty() && !pirate1.hasTreasure()) {
					move = move + movesBank;
					game.debug("movesBank = " + movesBank);
				}

				if (location != null && move > 0) {
					List<Location> possibleLocations = game.getSailOptions(pirate1, location, move);
					for (Location loc : possibleLocations) {
						if (!takenLocations.contains(loc) && (!game.isOccupied(loc) || goneAway)
								&& (!occupiedTargets.contains(loc))) {
							takenLocations.add(loc);
						}
					}
					Location loc = findSafestLocation(game, takenLocations, game.allEnemyPirates());
					if (loc != null) {
						game.setSail(pirate1, loc);
						game.debug(
								"Pirate " + pirate1.getId() + " is going " + move + " steps to " + loc.toString());
						game.debug("movesBank = " + movesBank);
						occupiedTargets.add(loc);
					}
				}
			}
		}
	}

	private void gameWithOnePirate(PirateGame game) {
		int moves = 0;
		Location location1 = null;
		Pirate pirate = pirates.remove(0);
		if (pirate.getReloadTurns() == 0 && game.inRange(pirate, game.getEnemyPirate(0))) {
			game.attack(pirate, game.getEnemyPirate(0));
		} else {
			if (pirate.hasTreasure()) {
				location1 = pirate.getInitialLocation();
				moves = 1;
			} else if (pirate.getReloadTurns() != 0) {
				location1 = game.treasures().get(0).getLocation();
				moves = 6;
			} else {
				location1 = new Location(13, 16);
				moves = 6;
			}
			if (location1 != null) {
				List<Location> possibleLocations = game.getSailOptions(pirate, location1, moves);
				for (Location loc : possibleLocations) {
					if (!takenLocations.contains(loc) && (!game.isOccupied(loc))) {
						takenLocations.add(loc);
						game.setSail(pirate, loc);
						break;
					}
				}

			}

		}
	}

	private void gameWithTwoPirates(PirateGame game) {
		Pirate attacker = null;
		while (!pirates.isEmpty()) {
			boolean goanyway = false;
			int move = 0;
			Pirate pirate1 = pirates.remove(0);
			Location location = null;
			Pirate ClosestEnemy = findClosestShip(game, pirate1, game.enemySoberPirates());
			Pirate closestEnemyWithTreasure = findClosestShip(game, pirate1, game.enemyPiratesWithTreasures());
			String[] power = pirate1.getPowerups();
			Pirate enemyPirate = enemyWithPowerup(game);
			if (pirate1.getTurnsToSober() == 0 && !pirate1.isLost()) {
				if (pirate1.getReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)
						&& !pirate1.hasTreasure()) {
					game.debug("Pirate " + pirate1.getId() + " is attacking enemy " + ClosestEnemy.getId());
					game.attack(pirate1, ClosestEnemy);
				} else if (ClosestEnemy != null && ClosestEnemy.getReloadTurns() == 0 && !ClosestEnemy.hasTreasure()
						&& pirate1.getDefenseReloadTurns() == 0 && game.inRange(pirate1, ClosestEnemy)) {
					game.defend(pirate1);
					game.debug("Pirate " + pirate1.getId() + " is defending from enemy " + ClosestEnemy.getId());
				}
				else {
					if (pirate1.hasTreasure()) {
						game.debug("Pirate " + pirate1.getId() + " has treasure and is going home");
						location = pirate1.getInitialLocation();
						move = pirate1.getCarryTreasureSpeed();

					} else if (pirate1.getReloadTurns() == 0 && (attacker == null || game.treasures().isEmpty())) {
						attacker = pirate1;
						location = ClosestEnemy.getLocation();
						game.debug(
								"Pirate " + pirate1.getId() + " is going to attack enemy " + ClosestEnemy.getId()); // we're
						// limiting to only one attacker per turn
						move = Math.min(Math.min(3, movesBank), game.distance(pirate1.getLocation(), location));
					} else if (!game.powerups().isEmpty()) {
						List<Powerup> p = game.powerups();
						while (!p.isEmpty()) {
							Powerup power2 = p.remove(0);
							Pirate min = closestToPowerup(power2, game);
							if (min.compareTo(pirate1) == 0) {
								location = power2.getLocation();
							}
						}
					}
					else {
						Treasure closestTreasure = findBestTreasure(game, pirate1);
						if (closestTreasure != null) {
							location = closestTreasure.getLocation();
							move = movesBank - game.myPiratesWithTreasures().size();
						}
					}
				}

				movesBank -= move;
				game.debug("movesBank = " + movesBank);
				if (movesBank > 0 && pirates.isEmpty() && !pirate1.hasTreasure()) {
					move = move + movesBank;
				}
				if (location != null) {
					List<Location> possibleLocations = game.getSailOptions(pirate1, location, move);
					for (Location loc : possibleLocations) {
						if (!takenLocations.contains(loc) && (!game.isOccupied(loc) || goanyway)
								&& (!occupiedTargets.contains(loc))) {
							takenLocations.add(loc);
						}
					}
					Location loc = findSafestLocation(game, takenLocations, game.allEnemyPirates());
					if (loc != null) {
						game.setSail(pirate1, loc);
						game.debug(
								"Pirates " + pirate1.getId() + " is going " + move + " steps to " + loc.toString());
						occupiedTargets.add(loc);
					}

				}
			}
		}

	}

	private static Pirate enemyWithPowerup(PirateGame game) {
		List<Pirate> enemys = game.enemyPirates();
		Pirate p = null;
		while (!enemys.isEmpty()) {
			p = enemys.remove(0);
			if (p.getPowerups().length > 0)
				if (p.getPowerups()[0] != null || p.getPowerups()[1] != null)
					return p;
		}
		p = null;
		return p;

	}

	public static Location enemyNextLocation(PirateGame game, Pirate enemy, Location finishLocation) {
		List<Location> possibleLocations = game.getSailOptions(enemy, finishLocation, 1);
		Location location = possibleLocations.get(0);
		return location;
	}

	public static Pirate recentlyShot(PirateGame game, List<Pirate> pirates) {
		Pirate Pirates = null;
		int maxReloadTurns = 1;
		if (!pirates.isEmpty()) {
			Pirates = pirates.remove(0);
			maxReloadTurns = Pirates.getReloadTurns();
		}

		while (!pirates.isEmpty()) {
			Pirate tempPirate = pirates.remove(0);
			int tempmaxReloadTurns = tempPirate.getReloadTurns();
			if (tempmaxReloadTurns > maxReloadTurns) {
				maxReloadTurns = tempmaxReloadTurns;
				Pirates = tempPirate;
			}
		}
		return Pirates;

	}

	public static Location findSafestLocation(PirateGame game, List<Location> loc, List<Pirate> pir) {
		Location location = null;
		int minDistance = 0;
		if (!loc.isEmpty()) {
			location = loc.remove(0);
			minDistance = findFarestLoaction(game, location, pir);
		}

		while (!loc.isEmpty()) {
			Location tempLocation = loc.remove(0);
			int tempDistance = findFarestLoaction(game, tempLocation, pir);
			if (tempDistance < minDistance) {
				minDistance = tempDistance;
				location = tempLocation;
			}
		}
		return location;
	}

	public static int findFarestLoaction(PirateGame game, Location location, List<Pirate> pirates) {
		Pirate Pirates = null;
		int maxDistance = 0;
		if (!pirates.isEmpty()) {
			Pirates = pirates.remove(0);
			maxDistance = game.distance(location, Pirates.getLocation());
		}

		while (!pirates.isEmpty()) {
			Pirate tempPirate = pirates.remove(0);
			int tempDistance = game.distance(location, tempPirate.getLocation());
			if (tempDistance > maxDistance) {
				maxDistance = tempDistance;
			}
		}
		return maxDistance;

	}

	public static Pirate findClosestShip(PirateGame game, Pirate pirate, List<Pirate> pirates) {
		List<Pirate> closesPirate = pirates;
		Location myShipLocation = pirate.getLocation();
		Pirate Pirates = null;
		Pirate PirateWithTreasure = null;
		int minDistance = 0;
		if (!closesPirate.isEmpty()) {
			Pirates = closesPirate.remove(0);
			minDistance = game.distance(myShipLocation, Pirates.getLocation());
		}

		while (!closesPirate.isEmpty()) {
			Pirate tempPirate = closesPirate.remove(0);
			int tempDistance = game.distance(myShipLocation, tempPirate.getLocation());
			if (tempDistance < minDistance) {
				minDistance = tempDistance;
				Pirates = tempPirate;
				if (Pirates.hasTreasure())
					PirateWithTreasure = Pirates;
			}

		}
		if (PirateWithTreasure != null)
			return PirateWithTreasure;
		return Pirates;
	}

	private Treasure findBestTreasure(PirateGame game, Pirate pirate) {
		Location myShipLocation = pirate.getLocation();
		Treasure result = null;
		double maxRatio = 0;

		for (Treasure treasure : game.treasures()) {
			double distance = game.distance(myShipLocation, treasure.getLocation());
			double value = treasure.getValue();
			double ratio = value / distance;

			if (ratio > maxRatio) {
				maxRatio = ratio;
				result = treasure;
			}
		}

		return result;
	}

	public static Pirate closestToPowerup(Powerup p, PirateGame game) {
		List<Pirate> myPirate = game.mySoberPirates();
		Pirate minPirate = null;
		int minDistance = 0;
		if (!myPirate.isEmpty()) {
			minPirate = myPirate.remove(0);
			minDistance = game.distance(minPirate.getLocation(), p.getLocation());
		}
		while (!myPirate.isEmpty()) {
			Pirate tempPirate = myPirate.remove(0);
			int tempDist = game.distance(tempPirate.getLocation(), p.getLocation());
			if (tempDist < minDistance) {
				minDistance = tempDist;
				minPirate = tempPirate;
			}
		}
		return minPirate;
	}

	private Pirate findEnemyToAttack(Pirate ship, PirateGame game) {
		for (Pirate enemy : game.enemySoberPirates()) {
			if (attackedEnemies.contains(enemy)) {
				continue;
			}
			if (enemy != null && ship.getReloadTurns() == 0 && game.inRange(ship, enemy) && !ship.hasTreasure() &&
					enemy.getDefenseExpirationTurns() == 0) {
				return enemy;
			}
		}
		return null;
	}

	private boolean shouldDefend(Pirate ship, PirateGame game) {
		for (Pirate enemy : game.enemySoberPirates()) {
			if (enemy != null && enemy.getReloadTurns() == 0 && !enemy.hasTreasure() &&
					ship.getDefenseReloadTurns() == 0 && game.inRange(ship, enemy)) {
				return true;
			}
		}
		return false;
	}
}
