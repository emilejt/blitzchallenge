package codes.blitz.game.bot;

import codes.blitz.game.message.game.*;
import codes.blitz.game.message.game.commands.Command;
import codes.blitz.game.message.game.commands.CommandActionLookAt;
import codes.blitz.game.message.game.commands.CommandActionRotate;
import codes.blitz.game.message.game.commands.CommandActionShoot;


import java.io.IOException;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

import java.util.List;


public class Bot
{
    private int direction;
    private List<String> hitIDMeteorites;

    public Bot()
    {
        System.out.println("Initializing your super duper mega bot.");
        // initialize some variables you will need throughout the game here
        this.direction = 1;
        hitIDMeteorites = new ArrayList<String>();
    }

    /*
     * Here is where the magic happens. I bet you can do better ;)
     */
    public Command getCommand(GameMessage gameMessage) throws IOException {

        Command command =  new Command();
        List<Meteor> meteorites;

        Vector cannonPos = gameMessage.cannon().position();
        double rocketSpeed = gameMessage.constants().rockets().speed();
        double rocketSize = gameMessage.constants().rockets().size();
        double mapWidth = gameMessage.constants().world().width();
        double mapLength = gameMessage.constants().world().height();


        if (gameMessage.cannon().orientation() >= 45) {
            this.direction = -1;
        } else if (gameMessage.cannon().orientation() <= -45) {
            this.direction = 1;
        }
        double minX = Double.MAX_VALUE;
        Meteor closestMeteorite = null;
        meteorites = gameMessage.meteors();

        Comparator<Meteor> comparator = new Comparator<Meteor>() {
            @Override
            public int compare(Meteor m1, Meteor m2) {
                double distance1 = m1.position().x();
                double distance2 = m2.position().x();
                return Double.compare(distance2, distance1); // Note the order for descending
            }
        };

        PriorityQueue<Meteor> closestMeteorites = new PriorityQueue<>(5, comparator);

        for (Meteor meteorite : meteorites) {
            if (meteorite.position().x() > cannonPos.x() + 50 && meteorite.position().y()>70 && meteorite.position().y()<mapLength-70 && !hitIDMeteorites.contains(meteorite.id())) {

                closestMeteorites.add(meteorite);
                if (closestMeteorites.size() > 5) {
                    closestMeteorites.poll(); // Removes the farthest meteorite if more than 3 are in the queue
                }
            }
        }

        List<Meteor> closestMeteoritesList = new ArrayList<>();
        while (!closestMeteorites.isEmpty()) {
            // Add from the priority queue into our list, which will be in ascending order now
            closestMeteoritesList.add(0, closestMeteorites.poll());
        }
        if(!closestMeteoritesList.isEmpty()) {
            Vector bestAimPosition = null;
            Meteor bestMeteorite = null;

            for (Meteor meteorite : closestMeteoritesList) {
                Vector aimPosition = calculateInterceptPoint(cannonPos, rocketSpeed, meteorite, rocketSize);

                if (meteorite.meteorType().equals(MeteorType.SMALL) && aimPosition.x() > cannonPos.x() + 20) {
                    bestAimPosition = aimPosition;
                    bestMeteorite = meteorite;
                    break; // Small is highest priority, so we can break the loop if we find it
                } else if (meteorite.meteorType().equals(MeteorType.MEDIUM) && aimPosition.x() > cannonPos.x() + 10) {
                    // If we already found a Medium, no need to replace it with another Medium
                    if (bestMeteorite == null || !bestMeteorite.meteorType().equals(MeteorType.MEDIUM)) {
                        bestAimPosition = aimPosition;
                        bestMeteorite = meteorite;
                        // Don't break, because there might still be a Small type ahead
                    }
                } else if (meteorite.meteorType().equals(MeteorType.LARGE) && aimPosition.x() > cannonPos.x() + 5) {
                    // Only set the bestAimPosition to Large if we haven't found a Small or Medium
                    if (bestMeteorite == null) {
                        bestAimPosition = aimPosition;
                        bestMeteorite = meteorite;
                        // Don't break, because there might still be a Small or Medium type ahead
                    }
                }
            }
            command.addAction(new CommandActionLookAt(bestAimPosition));
            if(gameMessage.cannon().cooldown()==0) {
                command.addAction(new CommandActionShoot());
                hitIDMeteorites.add(bestMeteorite.id());
            }
        }
        return command;
    }

    public Vector calculateInterceptPoint(Vector cannonPos, double rocketSpeed, Meteor closestMeteorite, double rocketRadius) {
        Vector meteoriteInitialPos = closestMeteorite.position();
        Vector meteoriteVelocity = closestMeteorite.velocity();
        double meteoriteRadius = closestMeteorite.size();
        // Initial guess for the intercept time is the time taken to reach the meteorite's current Vector
        double timeGuess = Math.sqrt(Math.pow(meteoriteInitialPos.x() - cannonPos.x(), 2) + Math.pow(meteoriteInitialPos.y() - cannonPos.y(), 2)) / rocketSpeed;
        Vector interceptPoint = new Vector(0, 0);
        double errorTolerance = 1e-3; // Define an error tolerance for the iterative process
        double deltaTime;
        double combinedRadii = meteoriteRadius + rocketRadius;

        do {
            // Predict meteorite's future Vector

            double x = meteoriteInitialPos.x() + meteoriteVelocity.x() * timeGuess;
            double y = meteoriteInitialPos.y() + meteoriteVelocity.y() * timeGuess;
            interceptPoint = new Vector(x,y);
            // Calculate new time to reach the predicted Vector, accounting for the radii
            double distanceToIntercept = Math.sqrt(Math.pow(interceptPoint.x() - cannonPos.x(), 2) + Math.pow(interceptPoint.y() - cannonPos.y(), 2)) - combinedRadii;
            double newTimeGuess = distanceToIntercept / rocketSpeed;

            // Ensure time is not negative due to radii being larger than distance at first guess
            if (newTimeGuess < 0) {
                newTimeGuess = 0;
            }

            // Calculate the difference between the new time guess and the previous time guess
            deltaTime = Math.abs(newTimeGuess - timeGuess);

            // Update the time guess for the next iteration
            timeGuess = newTimeGuess;

        } while (deltaTime > errorTolerance && timeGuess > 0); // Continue until the change in time is within the error tolerance or time guess is zero

        return interceptPoint;
    }
}

