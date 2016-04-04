package bots;

import java.nio.file.ClosedWatchServiceException;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;

import pirates.game.Direction;
import pirates.game.Location;
import pirates.game.Powerup;
import pirates.game.SpeedPowerup;
import pirates.game.Treasure;
import pirates.game.Pirate;
import pirates.game.PirateBot;
import pirates.game.PirateGame;
import java.util.Random;

public class MyBot implements PirateBot {

	final int MOVES_FOR_ATTACKER = 3;
	Random rand = new Random();

	@Override
	public void doTurn(PirateGame game) {
		int movesBank = game.getActionsPerTurn(); // Number of moves for this
													// turn

		List<Pirate> pWithTreasures = game.myPiratesWithTreasures();
		List<Pirate> pWithoutTreasures = game.myPiratesWithoutTreasures();

		List<Pirate> unableToShoot = new ArrayList<Pirate>();

		for (Pirate p : pWithoutTreasures) // Fills unableToShoot list
			if (p.getReloadTurns() != 0)
				unableToShoot.add(p);

		// List pirates has all the pirates
		List<Pirate> pirates = new ArrayList<>(pWithTreasures);
		pirates.addAll(pWithoutTreasures);

		List<Location> takenLocations = new ArrayList<Location>();
		List<Location> occupiedTargets = new ArrayList<Location>();

		List<Pirate> enemiesUnderAttack = new ArrayList<>();

		while (!pirates.isEmpty()) { // Loop through pirates
			int moves = 0;

			List<Pirate> sober = new ArrayList<>();
			for (Pirate p : game.enemySoberPirates())
				if (!enemiesUnderAttack.contains(p))
					sober.add(p);

			List<Pirate> soberWithTreasure = new ArrayList<>();
			for (Pirate p : game.enemyPiratesWithTreasures())
				if (!enemiesUnderAttack.contains(p))
					soberWithTreasure.add(p);

			Pirate currPirate = pirates.remove(0);
			Location destination = null;

			Pirate closestEnemy = findClosestShip(game, currPirate, sober);
			Pirate closestEnemyWithTreasure = findClosestShip(game, currPirate, soberWithTreasure);

			if (currPirate.getTurnsToSober() == 0 && !currPirate.isLost()) {
				if (closestEnemy != null && closestEnemy.getReloadTurns() == 0 && !closestEnemy.hasTreasure()
						&& currPirate.getDefenseReloadTurns() == 0 && game.inRange(currPirate, closestEnemy)) {
					game.defend(currPirate);
					game.debug("Pirate " + currPirate.getId() + " is defending from enemy " + closestEnemy.getId());
				}

				else if (currPirate.getReloadTurns() == 0
						&& (closestEnemy != null && game.inRange(currPirate, closestEnemy))
						&& !currPirate.hasTreasure()) {
					game.debug("Pirate " + currPirate.getId() + " is attacking enemy " + closestEnemy.getId());
					game.attack(currPirate, closestEnemy);
					enemiesUnderAttack.add(closestEnemy);
				}

				else if (closestEnemy != null && closestEnemy.getReloadTurns() == 0 && !closestEnemy.hasTreasure()
						&& currPirate.getDefenseReloadTurns() == 0 && game.inRange(currPirate, closestEnemy)) {
					game.defend(currPirate);
					game.debug("Pirate " + currPirate.getId() + " is defending from enemy " + closestEnemy.getId());
				}

				else {

					if (currPirate.hasTreasure()) {
						game.debug("Pirate " + currPirate.getId() + " has treasure and is going home");

						Powerup p;
						if (currPirate.getPowerups().length > 0 && (currPirate.getPowerups()[0].equals("Speed")
								|| currPirate.getPowerups()[1].equals("Speed")))
							p = null;
						else
							p = getPowerUpInTheWay(game, currPirate);
							destination = p == null ? currPirate.getInitialLocation() : p.getLocation();
						moves = currPirate.getCarryTreasureSpeed();
					}

					else if (!unableToShoot.isEmpty() && currPirate.getReloadTurns() != 0
							&& closestEnemyWithTreasure != null && !currPirate.hasTreasure()) {
						destination = enemyNextLocation(game, closestEnemyWithTreasure,
								closestEnemyWithTreasure.getInitialLocation());

						enemiesUnderAttack.add(closestEnemyWithTreasure);
						moves = Math.min(Math.min(MOVES_FOR_ATTACKER, movesBank),
								game.distance(currPirate.getLocation(), destination));

						game.debug("Pirate " + currPirate.getId() + " is going to sink enemy "
								+ closestEnemyWithTreasure.getId());
					}

					else {
						List<Treasure> closesTreasure = game.treasures();
						Treasure closestTreasure = findBestTreasure(game, currPirate, closesTreasure);

						if (closestTreasure != null) {
							destination = closestTreasure.getLocation();
							moves = Math.min(Math.min(MOVES_FOR_ATTACKER, movesBank),
									game.distance(currPirate.getLocation(), destination));
						}
					}
				}

				movesBank -= moves;
				game.debug("movesBank = " + movesBank);

				if (movesBank > 0 && pirates.isEmpty() && !currPirate.hasTreasure()) {
					moves = moves + movesBank;
					game.debug("movesBank = " + movesBank);
				}

				if (destination != null) {
					List<Location> possibleLocations = game.getSailOptions(currPirate, destination, moves);
					for (Location loc : possibleLocations) {
						if (!takenLocations.contains(loc) && (!game.isOccupied(loc))
								&& (!occupiedTargets.contains(loc))) {
							takenLocations.add(loc);
						}
					}

					Location loc = findSafestLocation(game, takenLocations, game.allEnemyPirates());

					if (loc != null) {
						game.setSail(currPirate, loc);
						game.debug(
								"Pirates " + currPirate.getId() + " is going " + moves + " steps to " + loc.toString());
						occupiedTargets.add(loc);
					}
				}
			}
		}
	}

	private static Powerup getPowerUpInTheWay(PirateGame game, Pirate pirate) {
		List<Powerup> powerUps = game.powerups();
		int minTurns = game.distance(pirate, pirate.getInitialLocation()) / pirate.getCarryTreasureSpeed();
		int tempTurns = 0;
		Powerup powerUp = null;
		if (powerUps.isEmpty()) {
			while (!powerUps.isEmpty()) {
				Powerup tempPowerUp = powerUps.remove(0);
				if (tempPowerUp.getType().equals("Speed"))
					tempTurns = game.distance(pirate, tempPowerUp.getLocation())
							+ game.distance(powerUp.getLocation(), pirate.getInitialLocation())
							- (powerUp.getActiveTurns()) * pirate.getCarryTreasureSpeed() + powerUp.getActiveTurns();
				if (tempTurns < minTurns) {
					minTurns = tempTurns;
					powerUp = tempPowerUp;
				}
				return powerUp;
			}
		}
		return null;
	}

	private static Pirate enemyWithPowerup(PirateGame game) {
		List<Pirate> enemys = game.enemyPirates();
		Pirate p = null;

		while (!enemys.isEmpty()) {
			p = enemys.remove(0);

			if (p.getPowerups().length > 0 && (p.getPowerups()[0] != null || p.getPowerups()[1] != null))
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

			if (tempDistance > maxDistance)
				maxDistance = tempDistance;
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

	public static Treasure findBestTreasure(PirateGame game, Pirate pirate, List<Treasure> closesTreasure) {
		if (closesTreasure == null)
			return null;

		Location myShipLocation = pirate.getLocation();
		Treasure treasure = null;
		double maxRatio = 0;

		while (!closesTreasure.isEmpty()) {
			Treasure tempTreasure = closesTreasure.remove(0);
			int tempDistance = game.distance(myShipLocation, tempTreasure.getLocation());
			int value = tempTreasure.getValue();

			double ratio = value / tempDistance;

			if (ratio > maxRatio) {
				maxRatio = ratio;
				treasure = tempTreasure;
			}
		}

		return treasure;
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
}
