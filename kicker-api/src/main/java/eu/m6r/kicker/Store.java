package eu.m6r.kicker;

import eu.m6r.kicker.models.Match;
import eu.m6r.kicker.models.State;
import eu.m6r.kicker.models.Team;
import eu.m6r.kicker.models.Tournament;
import eu.m6r.kicker.models.User;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.persistence.TypedQuery;


public class Store implements Closeable {

    private final SessionFactory sessionFactory;

    public Store() {
        this.sessionFactory = new Configuration().configure().buildSessionFactory();
    }

    private Session newSession() {
        return sessionFactory.openSession();
    }

    public void newTournament(final List<User> players) {
        try (final Session session = newSession()) {

            Transaction tx = session.beginTransaction();

            for (User player : players) {
                session.saveOrUpdate(player);
            }


            Collections.shuffle(players);
            Tournament tournament = new Tournament();
            tournament.state = State.RUNNING;
            Team teamA = new Team();
            teamA.player1 = players.get(0);
            teamA.player2 = players.get(1);

            session.save(teamA);
            tournament.teamA = teamA;

            Team teamB = new Team();
            teamB.player1 = players.get(2);
            teamB.player2 = players.get(3);

            session.save(teamB);
            tournament.teamB = teamB;

            tournament.id = (int) session.save(tournament);
            addMatch(tournament);
            tx.commit();
        }
    }

    private void addMatch(final Tournament tournament) {
        Match match = new Match();
        tournament.matches.add(match);
    }

    public void addMatch(final int tournamentId) {
        try (final Session session = newSession()) {
            final Transaction tx = session.beginTransaction();

            final Tournament tournament = session.get(Tournament.class, tournamentId);

            addMatch(tournament);

            tx.commit();
        }
    }

    public boolean hasRunningTournament() {
        try (final Session session = newSession()) {
            return !session.createNamedQuery("get_tournaments_with_state")
                    .setParameter("state", State.RUNNING).list().isEmpty();
        }
    }

    public List<Tournament> getTournaments() {
        try (final Session session = newSession()) {
            TypedQuery<Tournament> query = session.createNamedQuery(
                    "get_tournaments", Tournament.class);
            return query.getResultList();
        }
    }

    public void updateTournament(final Tournament tournament) {
        try (final Session session = newSession()) {
            Transaction tx = session.beginTransaction();
            for (Match match : tournament.matches) {
                System.out.println(match);
                //session.saveOrUpdate(match);
            }

            System.out.println(tournament);
            session.saveOrUpdate(tournament);
            tx.commit();
        }
    }

    public Tournament getRunningTournament() {
        try (final Session session = newSession()) {
            TypedQuery<Tournament> query = session.createNamedQuery(
                    "get_tournaments_with_state", Tournament.class).setParameter("state", State.RUNNING);
            return query.getSingleResult();
        }
    }

    @Override
    public void close() throws IOException {
        sessionFactory.close();
    }
}
