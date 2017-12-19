package com.example.eddystudio.bartable.HomePage;


import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.example.eddystudio.bartable.R;
import com.example.eddystudio.bartable.Repository.Repository;
import com.example.eddystudio.bartable.Repository.Response.EstimateResponse.Bart;
import com.example.eddystudio.bartable.Repository.Response.EstimateResponse.Etd;
import com.example.eddystudio.bartable.Repository.Response.Stations.BartStations;
import com.example.eddystudio.bartable.Uilts.BaseRecyclerViewAdapter;
import com.example.eddystudio.bartable.Uilts.CardSwipeController;
import com.example.eddystudio.bartable.Uilts.SwipeControllerActions;
import com.example.eddystudio.bartable.databinding.FragmentHomePageBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


/**
 * A simple {@link Fragment} subclass.
 */
public class HomePageRecyclerViewFragment extends Fragment {

    //private final HomePageRecyclerViewItemModel viewModel = new HomePageRecyclerViewItemModel();
    private FragmentHomePageBinding binding;
    private Repository repository;
    private static String selectedStation="DALY";
    private static int sinpperPos = 0;
    private final ArrayList<String> stationList = new ArrayList<>();
    private final ArrayList<String> stationListSortcut = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private final HomePageViewModel homePageViewModel = new HomePageViewModel();
    private SharedPreferences preferences;
    private Set<String> dashboardRouts;
    private final ArrayList<String> EtdStations = new ArrayList<>();
    private final static String DASHBOARDROUTS = "dashboardRouts";


    public HomePageRecyclerViewFragment() {
        // Required empty public constructor
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("selectedStation",selectedStation);
        outState.putInt("spinnerPos", sinpperPos);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            sinpperPos = savedInstanceState.getInt("spinnerPos");
            selectedStation = savedInstanceState.getString("selectedStation");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        repository = new Repository();
        //binding = DataBindingUtil.setContentView(getActivity(), R.layout.fragment_home_page);
        binding = FragmentHomePageBinding.inflate(inflater, container, false);

        ((AppCompatActivity)getActivity()).setSupportActionBar((Toolbar) binding.appToolbar);
        binding.setVm(homePageViewModel);
        spinnerAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, stationList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.stationSpinner.setAdapter(spinnerAdapter);
        binding.stationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedStation = stationListSortcut.get(i);
                sinpperPos = i;
                init(selectedStation);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        binding.swipeRefreshLy.setOnRefreshListener(() -> init(selectedStation));
        preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        dashboardRouts = preferences.getStringSet(DASHBOARDROUTS, new HashSet<>());
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        init(selectedStation);
        getAllStations();
        CardSwipeController cardSwipeController = new CardSwipeController(new SwipeControllerActions() {
            @Override
            public void onRightClicked(int position) {
                super.onRightClicked(position);
                String rout = selectedStation+"-"+EtdStations.get(position);
                dashboardRouts.add(rout);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putStringSet(DASHBOARDROUTS,  dashboardRouts);
                editor.apply();
                Snackbar.make(binding.recylerView,"Added " + rout +" to dashboard", Snackbar.LENGTH_LONG).show();

            }
        });
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(cardSwipeController);
        itemTouchHelper.attachToRecyclerView(binding.recylerView);
        binding.recylerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
                cardSwipeController.onDraw(c);
            }
        });
    }

    private void init(String stationShort) {

        repository.getEstimate(stationShort)
                .doOnSubscribe(ignored -> binding.swipeRefreshLy.setRefreshing(true))
                .observeOn(AndroidSchedulers.mainThread())
                .map(bart -> getEtd(bart))
                .concatMap(Observable::fromArray)
                .map(etds -> convertToVM(etds))
                .doOnNext(data -> {
                    int resId = R.anim.layout_animation_fall_down;
                    LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(getActivity(), resId);
                    binding.recylerView.setLayoutAnimation(animation);
                    BaseRecyclerViewAdapter adapters = new HomePageRecyclerViewAdapter(data,binding.recylerView.getId(),R.layout.home_page_single_recycler_view_item);
                    binding.recylerView.setAdapter(adapters);
                    binding.recylerView.setNestedScrollingEnabled(false);
                    binding.recylerView.setLayoutManager(new GridLayoutManager(getActivity(), 1));
                })
                .doOnError(this::handleError)
                .doOnComplete(() -> binding.swipeRefreshLy.setRefreshing(false))
                .subscribe();
    }

    private void getAllStations(){
        repository.getStations()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map(station -> getAllStations(station))
                .concatMap(Observable::fromArray)
                .doOnNext(stations -> setupSinnper(stations))
                .doOnNext(ignored -> binding.stationSpinner.setSelection(sinpperPos))
                .doOnError(this::handleError)
                .subscribe();
    }

    private void setupSinnper(List<com.example.eddystudio.bartable.Repository.Response.Stations.Station> stations) {

        for (int i = 0; i < stations.size(); ++i){
            stationList.add(stations.get(i).getName());
            stationListSortcut.add(stations.get(i).getAbbr());
        }
        spinnerAdapter.notifyDataSetChanged();
    }

    private List<com.example.eddystudio.bartable.Repository.Response.Stations.Station> getAllStations(BartStations station) {
        return station.getRoot().getStations().getStation();
    }

    private void handleError(Throwable throwable) {
        Log.e("error", "error on getting response", throwable);
        Snackbar.make(binding.recylerView, "error on loading", Snackbar.LENGTH_LONG).show();
    }

    private ArrayList<HomePageRecyclerViewItemModel> convertToVM(List<Etd> stations) {
        EtdStations.clear();
        ArrayList<HomePageRecyclerViewItemModel> vmList = new ArrayList<>();
        for (int i = 0; i < stations.size(); ++i){
            vmList.add(new HomePageRecyclerViewItemModel(stations.get(i)));
            EtdStations.add(stations.get(i).getAbbreviation());
        }
        return vmList;
    }

    private List<Etd> getEtd(Bart bart) {
        Log.d("destination", bart.toString());
        return bart.getRoot().getStation().get(0).getEtd();
    }

}