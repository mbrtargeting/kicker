/*
 * This file is part of kicker (https://github.com/mbrtargeting/kicker).
 * Copyright (c) 2019 Jan Graßegger.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import _ from 'lodash';
import { Observable } from 'rxjs';

import { Match, State, Team, Tournament } from '../models/tournament';

const TOURNAMENT_URL = '/api/tournament';

@Injectable()
export class TournamentController {

  private updateInProgress = 0;
  private tournament: Tournament;

  private undoStack: Tournament[] = [];
  private id: string;

  constructor(public http: HttpClient) {
  }

  canUndo() {
    return !_.isEmpty(this.undoStack);
  }

  undo() {
    this.tournament = this.undoStack.pop();
    this.push();
  }

  recordState() {
    this.undoStack.push(_.cloneDeep(this.tournament));
  }

  setId(id: string) {
    this.id = id;
  }

  addGoal(team: string): Promise<number> {
    return this.get()
      .then(() => {
        if (!team) {
          return;
        }

        this.recordState();
        const running = this.findRunning();
        if (!running || running[team] >= 6) {
          return;
        }
        running[team] += 1;
        return this.push().toPromise();
      });
  }

  checkAndUpdateTournamentState(): Promise<State> {
    if (this.tournament.state === State.FINISHED) {
      return Promise.resolve(State.FINISHED);
    }
    const wins = this.countWins(this.tournament.matches);
    const maxWins = (this.tournament.bestOfN + 1) / 2;

    if (Math.max(wins['teamA'], wins['teamB']) >= maxWins) {
      return this.finishTournament().then(() => this.tournament.state);
    }

    return Promise.resolve(this.tournament.state);
  }

  getRunningMatch(): Promise<Match> {
    return this.pull().toPromise()
      .then(tournament => {
        if (!tournament) {
          return;
        }

        return this.checkAndUpdateTournamentState().then(state => {
          const running = this.findRunning();
          if (running) {
            return running;
          }

          if (state === State.RUNNING) {
            return this.newMatch().then(() => this.getRunningMatch());
          }
          return;
        });
      });
  }

  static getWinner(match: Match): string {
    if (!match) {
      return;
    }
    if (match.teamA >= 6) {
      return 'teamA';
    }
    if (match.teamB >= 6) {
      return 'teamB';
    }
  }

  getWinnerTeam(match): Promise<Team> {
    return this.get().then(tournament => {
      const winner = TournamentController.getWinner(match);
      if (!winner) {
        return;
      }
      return tournament[winner];
    });
  }

  getTeamNames(): Promise<{ [key: string]: string }> {
    return this.getWins().then(wins => {
      const matchCount = wins ? wins['teamA'] + wins['teamB'] : 0;
      if (matchCount % 2 === 0) {
        return {
          left: 'teamA',
          right: 'teamB',
        };
      }
      return {
        left: 'teamB',
        right: 'teamA',
      };
    });
  }

  getTeams(): Promise<{ [key: string]: Team }> {
    return this.get().then(tournament => _.pick(tournament, ['teamA', 'teamB']));
  }

  getBestOfN(): Promise<number> {
    return this.get()
      .then(tournament => tournament.bestOfN);
  }

  newMatch(): Promise<Tournament> {
    return this.get()
      .then(() => this.http
        .post(this.tournamentUrl() + '/match', '')
        .toPromise()
      )
      .then(() => this.pull().toPromise());
  }

  getWins() {
    return this.get().then(tournament => {
      return this.countWins(tournament.matches);
    });
  }

  countWins(matches: Match[]): { [key: string]: number } {
    return _.filter(matches, match => {
      return match.state === State.FINISHED || match.state === State[State.FINISHED];
    })
      .reduce((memo, match) => {
        const winner = TournamentController.getWinner(match);
        memo[winner] += 1;
        return memo;
      }, { teamA: 0, teamB: 0 });
  }

  finishMatch(): Promise<boolean> {
    return this.get()
      .then(tournament => {
        const running = this.findRunning();
        running.state = State.FINISHED;

        const wins = this.countWins(tournament.matches);
        const playedBestOfN = _(wins).values().max() * 2 - 1;
        return this.tournament.bestOfN === playedBestOfN;
      })
      .then(finished => {
        return this.push().toPromise().then(() => finished);
      });
  }

  finishTournament(rematch = false): Promise<any> {
    return this.push().toPromise()
      .then(() => {
        this.tournament = undefined;

        const data = new HttpParams()
          .set('rematch', rematch.toString());
        return this.http
          .post(this.tournamentUrl() + '/finish', '', { params: data })
          .toPromise();
      });
  }

  cancelMatch(): Observable<{}> {
    this.recordState();
    return this.http.delete(this.tournamentUrl());
  }

  getUpdateInProgress() {
    return this.updateInProgress > 0;
  }

  private findRunning(): Match {
    return _.find(this.tournament.matches, { state: State.RUNNING });
  }

  private get(): Promise<Tournament> {
    if (this.tournament) {
      return Promise.resolve(this.tournament);
    }

    return this.pull().toPromise();
  }

  private tournamentUrl(): string {
    return TOURNAMENT_URL + '/' + this.id;
  }

  private pull(): Observable<Tournament> {
    return this.http.get<Tournament>(this.tournamentUrl() + '/running')
      .map((tournament: Tournament) => {
        this.tournament = tournament;
        return tournament;
      });
  }

  private push(): Observable<number> {
    this.updateInProgress += 1;
    return this.http.put(this.tournamentUrl(), this.tournament)
      .map(() => this.updateInProgress -= 1);
  }
}
