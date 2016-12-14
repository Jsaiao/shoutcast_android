package com.zilideus.jukebox.fragment;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.zilideus.jukebox.ListAdapterStations;
import com.zilideus.jukebox.MyService;
import com.zilideus.jukebox.R;
import com.zilideus.jukebox.flags.Flags;
import com.zilideus.jukebox.flags.Url_format;
import com.zilideus.jukebox.network.DownloadContent;
import com.zilideus.jukebox.parser.ParserXMLtoJSON;
import com.zilideus.jukebox.parser.Station;
import com.zilideus.jukebox.parser.StationList;
import com.zilideus.jukebox.parser.TuneIn;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 */
public class ListFragment extends Fragment {


    private String url;
    private SharedPreferences preferences;

//    execute(url);

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        preferences = getActivity().getSharedPreferences(Flags.SETTINGS, Context
                .MODE_PRIVATE);

        url = preferences.getString(Flags.JSON_URL, new Url_format().getTopStationsXML(Flags.DEV_ID, "50",
                null, null));

        return inflater.inflate(R.layout.station_list, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        if (DownloadContent.isAvailable(getActivity())) {

            DownloadAndShowList downloadAndShowList = new DownloadAndShowList(view);
            downloadAndShowList.execute(url);

        } else {
            Toast.makeText(getActivity(), "Network connection not available", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Class downloads the list and shows on the ListView
     */
    private class DownloadAndShowList extends AsyncTask<String, Void, ArrayList<Station>> {
        ImageButton listButton;
        StationList stationList;
        ListView listView;
        ProgressBar progressBar;
        ParserXMLtoJSON parser;
        Url_format url_format;
        TuneIn tuneIn;
        private View view;

        public DownloadAndShowList(View view) {

            this.view = view;
        }


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressBar = (ProgressBar) view.findViewById(R.id.progressbarlist);
            progressBar.setVisibility(View.VISIBLE);
            parser = new ParserXMLtoJSON();
            url_format = new Url_format();
            listView = (ListView) view.findViewById(R.id.list_stations);

            listButton = (ImageButton) view.findViewById(R.id.but_media_list);
            if (listButton != null) {
                listButton.setEnabled(false);
            }


        }

        @Override
        protected ArrayList<Station> doInBackground(String... params) {
            try {
                String data = DownloadContent.downloadContent(params[0]);
                stationList = parser.getTopStationswithLIMIT(data);
                tuneIn = stationList.getTuneIn();
                if (stationList != null) {
                    return stationList.getArrayListStations();
                } else return null;

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(final ArrayList<Station> stations) {

            if (listView != null && stations != null && stations.size() > 0) {
                super.onPostExecute(stations);

                listView.setAdapter(new ListAdapterStations(getActivity().getApplicationContext(), R.layout.itemlistrow,
                        stations));
                listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        final Station station = stations.get(position);
                        if (station != null) {

                            DownloadSongDetailAndPlayOnClick downloadSongDetailAndPlayOnclick = new DownloadSongDetailAndPlayOnClick(station);
                            downloadSongDetailAndPlayOnclick.execute(station);
                        }
                    }
                });
                listButton.setEnabled(true);
                progressBar.setVisibility(View.GONE);
            } else {
                Toast.makeText(getContext(), "No station found", Toast.LENGTH_LONG).show();
                preferences.edit().putString(Flags.JSON_URL, new Url_format().getTopStationsXML(Flags
                        .DEV_ID, "30", null, null)).apply();
                if (progressBar != null && progressBar.isShown()) {
                    progressBar.setVisibility(View.GONE);
                }

            }

        }


    }

    class DownloadSongDetailAndPlayOnClick extends AsyncTask<Station, Void, ArrayList<Uri>> {
        ImageButton imageButtonPlay;
        String file;
        private Station station;

        DownloadSongDetailAndPlayOnClick(Station station) {
            this.station = station;
        }


        @Override
        protected void onPreExecute() {
            TextView textTitle, textDesc;

            super.onPreExecute();
            textTitle = (TextView) getActivity().findViewById(R.id.text_title);
            textDesc = (TextView) getActivity().findViewById(R.id.text_description);
            imageButtonPlay = (ImageButton) getActivity().findViewById(R.id
                    .but_media_play);

            Flags.SONG_TITLE = station.getName();
            Flags.SONG_DESCRIPTION = station.getCtqueryString();

            Flags.SONG_IMAGE_URL = station.getLogo();

            if (textDesc != null && textTitle != null) {
                textDesc.setText(station.getCtqueryString());
                textTitle.setText(station.getName());
            }
        }

        @Override
        protected ArrayList<Uri> doInBackground(Station... params) {
            ArrayList<String> m3u = DownloadContent.lineArray("http://yp.shoutcast" +
                    ".com/" + "/sbin/tunein-station.m3u" + "?id=" + params[0].getId());
            ArrayList<Uri> uriArrayList = new ArrayList<Uri>();

            for (int i = 0; i < m3u.size(); i++) {
                if (m3u.get(i).startsWith("http")) {
                    file = m3u.get(i);
                    file = file.replace("http", "icy");
                    uriArrayList.add(Uri.parse(file));
                    break;
                }
            }

            return uriArrayList;
        }

        @Override
        protected void onPostExecute(ArrayList<Uri> uriArrayList) {
            super.onPostExecute(uriArrayList);
            if (imageButtonPlay != null) {
                imageButtonPlay.setEnabled(true);
            }
            Intent intent = new Intent(getActivity(), MyService.class);
//                                            getActivity().startService(intent);
            getActivity().bindService(intent, new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    MyService.ServiceBinder servicBinder = (MyService.ServiceBinder) iBinder;
                    MyService myServiceEngine = servicBinder.getService();
//				  myServiceEngine.prepare(url,"icy://37.130.230.93:9092");
                    try {
                        myServiceEngine.prepare(file, station);
                    } catch (Exception ex) {
                        Log.e("Exception service", "while preparing for" + getClass().getName());
                    }

                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.e("disconnected", "service");
                }
            }, Context.BIND_AUTO_CREATE);

        }
    }
}
