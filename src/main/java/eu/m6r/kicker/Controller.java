package eu.m6r.kicker;

import eu.m6r.kicker.models.Match;
import eu.m6r.kicker.models.Player;
import eu.m6r.kicker.models.State;
import eu.m6r.kicker.models.Team;
import eu.m6r.kicker.models.Tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public enum Controller {
    INSTANCE;

    private final List<Player> players;

    private Tournament activeTournament;

    Controller() {
        this.players = new ArrayList<>();
        activeTournament = null;
    }

    public void startTournament() throws TournamentRunningException {
        startTournament(true, 3);
    }

    public synchronized void startTournament(final boolean shuffle, final int bestOfN)
            throws TournamentRunningException {
        if (hasRunningTournament()) {
            throw new TournamentRunningException();
        }

        List<Player> playerList = players;

        if (shuffle) {
            Collections.shuffle(playerList);
        }

        try (final Store store = new Store()) {
            final Team teamA = store.getTeam(playerList.get(0), playerList.get(1));
            final Team teamB = store.getTeam(playerList.get(2), playerList.get(3));
            this.activeTournament = new Tournament(bestOfN, teamA, teamB);
        }

        players.clear();
    }

    public synchronized void finishTournament() throws InvalidTournamentStateException {
        for (final Match match : activeTournament.matches) {
            if (match.state == State.RUNNING) {
                throw new InvalidTournamentStateException("Can't finish tournament if matches"
                                                          + "are still running!");
            }
        }

        activeTournament.state = State.FINISHED;

        try (final Store store = new Store()) {
            store.updateTournament(activeTournament);
            activeTournament = null;
        }
    }

    public List<Tournament> getTournaments() {
        try (final Store store = new Store()) {

            return store.getTournaments();
        }
    }

    public boolean hasRunningTournament() {
        return activeTournament != null;
    }

    public Tournament getRunningTournament() {
        return activeTournament;
    }

    public void updateTournament(final Tournament tournament) {
        this.activeTournament.matches = tournament.matches;
    }

    public boolean cancelRunningTournament() {
        if (!hasRunningTournament()) {
            return false;
        }

        activeTournament = null;
        return true;
    }

    public void newMatch()
            throws InvalidTournamentStateException, TournamentNotRunningException {
        if (activeTournament == null) {
            throw new TournamentNotRunningException();
        }

        int teamAWins = 0;
        int teamBWins = 0;

        for (final Match match : activeTournament.matches) {
            if (match.state == State.FINISHED) {
                if (match.teamA > match.teamB) {
                    teamAWins++;
                } else {
                    teamBWins++;
                }
            } else if (match.state == State.RUNNING) {
                throw new InvalidTournamentStateException("Can't create new match if matches "
                                                          + "are still running!");
            }
        }

        final int maxTeamWins = (activeTournament.bestOfN / 2) + 1;

        if (maxTeamWins <= Math.max(teamAWins, teamBWins)) {
            throw new InvalidTournamentStateException("Cannot create more matches than bestOfN.");
        }

        activeTournament.matches.add(new Match());
    }

    public String getPlayersString() {
        return players.stream().map(p -> String.format("<@%s>", p.id))
                .collect(Collectors.joining(", "));
    }

    public void addPlayer(final String playerId)
            throws TooManyUsersException, PlayerAlreadyInQueueException {
        try (final Store store = new Store()) {
            players.add(store.getPlayer(playerId));
        }

    }

    public String addPlayer(Player player)
            throws TooManyUsersException, PlayerAlreadyInQueueException,
                   TournamentRunningException {
        if (players.contains(player)) {
            throw new PlayerAlreadyInQueueException(player);
        }

        if (players.size() == 4) {
            throw new TooManyUsersException(player);
        }

        if (hasRunningTournament()) {
            return "A tournament is still running!";
        }

        players.add(player);

        if (players.size() == 4) {
            startTournament();

            Tournament tournament = getRunningTournament();
            return String.format("A new game started:%n <@%s> <@%s> vs. <@%s> <@%s>",
                                 tournament.teamA.player1.id, tournament.teamA.player2.id,
                                 tournament.teamB.player1.id, tournament.teamB.player2.id);
        }

        return String.format("Added %s to the queue.", player.name);
    }

    public void resetPlayers() {
        players.clear();
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public List<Player> getPlayersInQueue() {
        return new ArrayList<>(players);
    }

    public String getListOfPlayers() {
        List<String> playerNames = players.stream().map(player -> player.name)
                .collect(Collectors.toList());
        return String.join(",", playerNames);
    }

    public static class TooManyUsersException extends Exception {

        public TooManyUsersException(final Player player) {
            super(String.format("Unable to add %s to the game. Too many users in the queue. "
                                + "Please remove users from the queue or start a game.",
                                player.name));
        }
    }

    public static class PlayerAlreadyInQueueException extends Exception {

        public PlayerAlreadyInQueueException(final Player player) {
            super(String.format("%s is already in the queue!", player.name));
        }
    }

    public static class TournamentRunningException extends Exception {

        public TournamentRunningException() {
            super("A tournament is already running!");
        }
    }

    public static class TournamentNotRunningException extends Exception {

        public TournamentNotRunningException() {
            super("No tournament is running!");
        }
    }

    public static class InvalidTournamentStateException extends Exception {

        public InvalidTournamentStateException(final String message) {
            super(message);
        }
    }
}
